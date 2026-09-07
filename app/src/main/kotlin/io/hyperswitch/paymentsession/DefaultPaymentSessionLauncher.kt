package io.hyperswitch.paymentsession

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.HyperReactRuntime
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class DefaultPaymentSessionLauncher(
    activity: Activity,
    hsConfig: HyperswitchBaseConfiguration?,
    private var paymentSessionReactLauncher: SDKInterface = PaymentSessionReactLauncher(activity, hsConfig)
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

    /** This session's React runtime (host, emitter, router). Null on the WebView backend. */
    internal val reactRuntime: HyperReactRuntime?
        get() = (paymentSessionReactLauncher as? PaymentSessionReactLauncher)?.runtime

    override fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        super.initPaymentSession(sessionConfig)
        paymentSessionReactLauncher.sessionConfig = sessionConfig
        paymentSessionReactLauncher.prefetch()
    }

    suspend fun awaitReady() = paymentSessionReactLauncher.awaitReady()

    /** Session-level updateIntent; commits the base [sessionConfig] on success. */
    fun updateIntent(
        authorizationProvider: (onAuthorization: (String) -> Unit) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        paymentSessionReactLauncher.updateIntent(authorizationProvider) { result ->
            result.onSuccess { sessionConfig = PaymentSessionConfiguration(it) }
            onResult(result)
        }
    }

    private fun applySubscription(subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?) {
        subscribe ?: return
        val builder = PaymentEventSubscriptionBuilder()
        builder.subscribe()
        val (subscription, listener) = builder.build()
        reactRuntime?.eventEmitter?.setEventListener(listener, subscription)
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        applySubscription(subscribe)
        val isFragment =
            paymentSessionReactLauncher.presentSheet(sessionConfig, configuration)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        applySubscription(subscribe)
        val isFragment = paymentSessionReactLauncher.presentSheet(configurationMap)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        checkNotNull(reactRuntime) { "React runtime not initialised" }
            .sessionRouter.setSessionCallback(sessionConfig?.sdkAuthorization, savedPaymentMethodCallback)
        paymentSessionReactLauncher.recreateReactContext(configuration)
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
            val router = checkNotNull(reactRuntime) { "React runtime not initialised" }.sessionRouter
            router.setSessionCallback(sessionConfig?.sdkAuthorization) { handler ->
                if (continuation.isActive) continuation.resume(handler)
            }
            continuation.invokeOnCancellation {
                router.setSessionCallback(sessionConfig?.sdkAuthorization, null)
            }
            paymentSessionReactLauncher.recreateReactContext(configuration)
        }

}