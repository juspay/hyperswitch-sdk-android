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
import io.hyperswitch.react.ReactNativeController
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
     *
     * @throws IllegalStateException (SESSION_INIT_IN_PROGRESS via [Throwable.cause]) when the
     * same sdkAuthorization is being fetched in another in-progress session: retry once it
     * completes, or keep the session you already have.
     */
    override suspend fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        super.initPaymentSession(sessionConfig)
        // Await prefetch completion only; the payload lives in the JS PrefetchCache. A duplicate
        // init throws out of fetchPrefetch (SESSION_INIT_IN_PROGRESS) — the in-flight caller
        // owns the entry, so nothing here runs: no clear, no commit.
        val data = paymentSessionReactLauncher.fetchPrefetch(sessionConfig)
        /* A failed re-validation we OWN must not leave an earlier (e.g. cancelled) attempt's
           entry behind: the sheet would mount with minutes-old session tokens instead of
           fetching for itself. */
        if (data.isFailure) {
            paymentSessionReactLauncher.clearPrefetch(sessionConfig.sdkAuthorization)
        }
        paymentSessionReactLauncher.commitSession(sessionConfig)
    }

    /** Fetches the new intent's data without changing the active session. */
    suspend fun prepareIntentUpdate(
        sessionConfig: PaymentSessionConfiguration,
    ): Result<ReadableMap> = try {
        paymentSessionReactLauncher.fetchPrefetch(
            sessionConfig,
            headlessType = "updateIntent",
        )
    } catch (error: Throwable) {
        Result.failure(error)
    }

    fun commitIntentUpdate(sessionConfig: PaymentSessionConfiguration) {
        this.sessionConfig = sessionConfig
        paymentSessionReactLauncher.commitSession(sessionConfig)
    }

    fun clearPrefetch(sdkAuthorization: String) {
        paymentSessionReactLauncher.clearPrefetch(sdkAuthorization)
    }

    private fun applySubscription(subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?) {
        subscribe ?: return
        val builder = PaymentEventSubscriptionBuilder()
        builder.subscribe()
        val (subscription, listener) = builder.build()
        ReactNativeController.eventEmitter.setEventListener(listener, subscription)
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
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
        val authorization = sessionConfig?.sdkAuthorization.orEmpty()
        if (authorization.isEmpty()) {
            savedPaymentMethodCallback(PaymentSessionHandlerImpl.failed(missingSessionError()))
            return
        }
        val request = PendingHeadlessRequest(
            callback = savedPaymentMethodCallback,
            onTerminalResult = { resultAuthorization, result ->
                clearAfterTerminalResult(resultAuthorization, result)
            },
            currentSdkAuthorization = { paymentSessionReactLauncher.sessionConfig?.sdkAuthorization.orEmpty() },
        )
        if (!HeadlessRequestRegistry.tryRegister(
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
            if (HeadlessRequestRegistry.remove(authorization, request)) {
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
            val authorization = sessionConfig?.sdkAuthorization.orEmpty()
            if (authorization.isEmpty()) {
                continuation.resumeWithException(missingSessionError())
                return@suspendCancellableCoroutine
            }
            val request = PendingHeadlessRequest(
                callback = { handler ->
                    if (continuation.isActive) continuation.resume(handler)
                },
                onTerminalResult = { resultAuthorization, result ->
                    clearAfterTerminalResult(resultAuthorization, result)
                },
                currentSdkAuthorization = { paymentSessionReactLauncher.sessionConfig?.sdkAuthorization.orEmpty() },
            )
            if (!HeadlessRequestRegistry.tryRegister(
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
                HeadlessRequestRegistry.remove(authorization, request)
            }
            try {
                paymentSessionReactLauncher.startHeadlessTask(configuration)
            } catch (error: Throwable) {
                if (
                    HeadlessRequestRegistry.remove(authorization, request) &&
                    continuation.isActive
                ) {
                    continuation.resumeWithException(error)
                }
            }
        }


    companion object {
        private const val SAVED_METHODS_TIMEOUT_MS = 30_000L
    }

    private fun alreadyInProgressError(): IllegalStateException =
        IllegalStateException("Saved payment methods request already in progress").apply {
            initCause(Throwable("ALREADY_IN_PROGRESS"))
        }

    private fun missingSessionError(): IllegalStateException =
        IllegalStateException("sdkAuthorization must not be empty").apply {
            initCause(Throwable("MISSING_SESSION"))
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
