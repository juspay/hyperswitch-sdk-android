package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.HyperEventEmitter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class DefaultPaymentSessionLauncher(
    activity: Activity,
    config: HyperswitchBaseConfiguration?,
    customParams: Bundle?,
    private var paymentSessionReactLauncher: SDKInterface = PaymentSessionReactLauncher(activity, config)
) : BasePaymentSessionLauncher(
    activity,
    config,
    customParams
) {

    init {
        val publishableKey = config?.publishableKey
        if (publishableKey != null) {
            val loggingEndPoint = config.customConfig?.overrideCustomLoggingEndpoint?.takeIf { it.isNotEmpty() }
                ?: getLoggingUrl(publishableKey)
            HyperLogManager.initialise(publishableKey, loggingEndPoint)
            HyperLogManager.sendLogsFromFile(LogFileManager(activity))
        }
        paymentSessionReactLauncher.initializeReactNativeInstance()
    }

    override fun initPaymentSession(sdkAuthorization: String) {
        super.initPaymentSession(sdkAuthorization)
        Companion.sdkAuthorization = sdkAuthorization
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        isPresented = true
        if (subscribe != null) {
            val builder = PaymentEventSubscriptionBuilder()
            builder.subscribe()
            val (subscription, listener) = builder.build()
            HyperEventEmitter.setEventListener(listener, subscription)
        }
        val isFragment =
            paymentSessionReactLauncher.presentSheet(Companion.sdkAuthorization ?: "", configuration)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        isPresented = true
        if (subscribe != null) {
            val builder = PaymentEventSubscriptionBuilder()
            builder.subscribe()
            val (subscription, listener) = builder.build()
            HyperEventEmitter.setEventListener(listener, subscription)
        }
        val isFragment = paymentSessionReactLauncher.presentSheet(configurationMap)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
        isPresented = false
        GetPaymentSessionCallBackManager.setCallback(sdkAuthorization, savedPaymentMethodCallback)
        paymentSessionReactLauncher.recreateReactContext()
    }

    override suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler =
        suspendCancellableCoroutine { continuation ->
            isPresented = false
            GetPaymentSessionCallBackManager.setCallback(sdkAuthorization) { handler ->
                if (continuation.isActive) continuation.resume(handler)
            }
            continuation.invokeOnCancellation {
                GetPaymentSessionCallBackManager.setCallback(sdkAuthorization, null)
            }
            paymentSessionReactLauncher.recreateReactContext()
        }

    companion object {
        var isPresented: Boolean = false
        var sdkAuthorization: String? = null
    }
}