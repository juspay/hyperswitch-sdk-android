package io.hyperswitch.paymentsession

import android.app.Activity
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.HyperEventEmitter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class DefaultPaymentSessionLauncher(
    activity: Activity,
    hsConfig: HyperswitchBaseConfiguration?,
    private val paymentSessionReactLauncher: PaymentSessionReactLauncher =
        PaymentSessionReactLauncher(activity, hsConfig)
) : BasePaymentSessionLauncher(activity, hsConfig) {

    init {
        val publishableKey = hsConfig?.publishableKey
        if (publishableKey != null) {
            val loggingEndPoint =
                hsConfig.customConfig?.overrideEndpoints?.customLoggingEndpoint
                    ?.takeIf { it.isNotEmpty() }
                    ?: getLoggingUrl(publishableKey)
            HyperLogManager.initialise(publishableKey, loggingEndPoint)
            HyperLogManager.sendLogsFromFile(LogFileManager(activity))
        }
        paymentSessionReactLauncher.initializeReactNativeInstance()
    }

    /**
     * Initializes the session and fetches everything the sheet and headless flows need. Callers
     * await this before presenting anything; a failure only means the flows fall back to fetching
     * for themselves.
     */
    override suspend fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        super.initPaymentSession(sessionConfig)
        val data = paymentSessionReactLauncher
            .fetchPrefetch(sessionConfig)
            .getOrNull()
        paymentSessionReactLauncher.commitPrefetch(sessionConfig, data)
    }

    /** Fetches the new intent's data without changing the active session. */
    suspend fun prepareIntentUpdate(
        sessionConfig: PaymentSessionConfiguration,
    ): Result<ReadableMap> = paymentSessionReactLauncher.fetchPrefetch(
        sessionConfig,
        headlessType = "updateIntent",
    )

    fun commitIntentUpdate(
        sessionConfig: PaymentSessionConfiguration,
        prefetchedData: ReadableMap,
    ) {
        this.sessionConfig = sessionConfig
        paymentSessionReactLauncher.commitPrefetch(sessionConfig, prefetchedData)
    }

    fun clearPrefetch(sdkAuthorization: String) {
        paymentSessionReactLauncher.clearPrefetch(sdkAuthorization)
    }

    fun getPrefetchedApiData(): ReadableMap? = paymentSessionReactLauncher.prefetchedData

    private fun applySubscription(subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?) {
        subscribe ?: return
        val builder = PaymentEventSubscriptionBuilder()
        builder.subscribe()
        val (subscription, listener) = builder.build()
        HyperEventEmitter.setEventListener(listener, subscription)
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        isPresented = true
        applySubscription(subscribe)
        val isFragment =
            paymentSessionReactLauncher.presentSheet(sessionConfig, configuration)
        val authorization = sessionConfig?.sdkAuthorization.orEmpty()
        PaymentSheetCallbackManager.setCallback({ result ->
            clearAfterTerminalResult(authorization, result)
            resultCallback(result)
        }, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        isPresented = true
        applySubscription(subscribe)
        val isFragment = paymentSessionReactLauncher.presentSheet(configurationMap)
        val authorization = sessionConfig?.sdkAuthorization.orEmpty()
        PaymentSheetCallbackManager.setCallback({ result ->
            clearAfterTerminalResult(authorization, result)
            resultCallback(result)
        }, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        isPresented = false
        val authorization = sessionConfig?.sdkAuthorization.orEmpty()
        val request = PendingSavedMethodsRequest(
            callback = savedPaymentMethodCallback,
            onTerminalResult = { resultAuthorization, result ->
                clearAfterTerminalResult(resultAuthorization, result)
            },
            currentSdkAuthorization = { sessionConfig?.sdkAuthorization.orEmpty() },
        )
        if (!SavedMethodsRequestRegistry.tryRegister(
                authorization,
                request,
                SAVED_METHODS_TIMEOUT_MS,
                onTimeout = {
                    savedPaymentMethodCallback(
                        PaymentSessionHandlerImpl.failed(savedMethodsTimeoutError())
                    )
                },
            )
        ) {
            savedPaymentMethodCallback(
                PaymentSessionHandlerImpl.failed(alreadyInProgressError())
            )
            return
        }
        try {
            paymentSessionReactLauncher.startHeadlessTask(configuration)
        } catch (error: Throwable) {
            if (SavedMethodsRequestRegistry.remove(authorization, request)) {
                savedPaymentMethodCallback(PaymentSessionHandlerImpl.failed(error))
            }
        }
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        getCustomerSavedPaymentMethods(null, savedPaymentMethodCallback)
    }

    override suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
    ): PaymentSessionHandler =
        suspendCancellableCoroutine { continuation ->
            isPresented = false
            val authorization = sessionConfig?.sdkAuthorization.orEmpty()
            val request = PendingSavedMethodsRequest(
                callback = { handler ->
                    if (continuation.isActive) continuation.resume(handler)
                },
                onTerminalResult = { resultAuthorization, result ->
                    clearAfterTerminalResult(resultAuthorization, result)
                },
                currentSdkAuthorization = { sessionConfig?.sdkAuthorization.orEmpty() },
            )
            if (!SavedMethodsRequestRegistry.tryRegister(
                    authorization,
                    request,
                    SAVED_METHODS_TIMEOUT_MS,
                    onTimeout = {
                        if (continuation.isActive) {
                            continuation.resumeWithException(savedMethodsTimeoutError())
                        }
                    },
                )
            ) {
                continuation.resumeWithException(alreadyInProgressError())
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                SavedMethodsRequestRegistry.remove(authorization, request)
            }
            try {
                paymentSessionReactLauncher.startHeadlessTask(configuration)
            } catch (error: Throwable) {
                if (
                    SavedMethodsRequestRegistry.remove(authorization, request) &&
                    continuation.isActive
                ) {
                    continuation.resumeWithException(error)
                }
            }
        }


    companion object {
        var isPresented: Boolean = false
        private const val SAVED_METHODS_TIMEOUT_MS = 30_000L
    }

    private fun alreadyInProgressError(): IllegalStateException =
        IllegalStateException("Saved payment methods request already in progress").apply {
            initCause(Throwable("ALREADY_IN_PROGRESS"))
        }

    private fun savedMethodsTimeoutError(): IllegalStateException =
        IllegalStateException("Saved payment methods request timed out").apply {
            initCause(Throwable("HEADLESS_TIMEOUT"))
        }

    internal fun clearAfterTerminalResult(
        sdkAuthorization: String,
        result: PaymentResult,
    ) {
        if (result !is PaymentResult.Canceled) {
            clearPrefetch(sdkAuthorization)
        }
    }
}
