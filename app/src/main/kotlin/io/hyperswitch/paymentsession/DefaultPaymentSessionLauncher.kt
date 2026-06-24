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

    override fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        super.initPaymentSession(sessionConfig)
        val launcher = paymentSessionReactLauncher as PaymentSessionReactLauncher
        launcher.sessionConfig = sessionConfig
        launcher.isPrefetchTriggered = true
        launcher.prefetchedData = null
        paymentSessionReactLauncher.recreateReactContext(null, headlessType = "prefetch")
    }

    fun getPrefetchedApiData(): Pair<Boolean, ReadableMap?> {
        val launcher = paymentSessionReactLauncher as PaymentSessionReactLauncher
        return Pair(launcher.isPrefetchTriggered, launcher.prefetchedData)
    }

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
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        isPresented = true
        applySubscription(subscribe)
        val isFragment = paymentSessionReactLauncher.presentSheet(configurationMap)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        isPresented = false
        GetPaymentSessionCallBackManager.setCallback(sessionConfig?.sdkAuthorization, savedPaymentMethodCallback)
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
            isPresented = false
            GetPaymentSessionCallBackManager.setCallback(sessionConfig?.sdkAuthorization) { handler ->
                if (continuation.isActive) continuation.resume(handler)
            }
            continuation.invokeOnCancellation {
                GetPaymentSessionCallBackManager.setCallback(sessionConfig?.sdkAuthorization, null)
            }
            paymentSessionReactLauncher.recreateReactContext(configuration)
        }


    companion object {
        var isPresented: Boolean = false
    }
}