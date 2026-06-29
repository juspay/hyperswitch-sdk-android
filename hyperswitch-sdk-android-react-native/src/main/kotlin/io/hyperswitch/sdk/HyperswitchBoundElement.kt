package io.hyperswitch.sdk

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsheet.PaymentRequestData
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.utils.ConversionUtils
import io.hyperswitch.view.HyperswitchElement
import io.hyperswitch.view.PaymentResultListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class HyperswitchBoundElement internal constructor(
    private val paymentSession: PaymentSession,
    private val element: HyperswitchElement,
    configuration: PaymentSheet.Configuration? = null,
    subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
) {

    /** Secondary constructor accepting a raw configurationMap (mirrors the presentPaymentSheet overload). */
    internal constructor(
        paymentSession: PaymentSession,
        element: HyperswitchElement,
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
    ) : this(paymentSession, element, null, subscribe) {
        element.setConfiguration(ConversionUtils.convertMapToReadableMap(configurationMap))
    }

    init {
        val config = paymentSession.getHsConfig()
        if (config != null) {
            element.initWidget(config)
        }
        if (configuration != null) {
            element.setConfiguration(configuration)
        }
        if (subscribe != null) {
            val builder = PaymentEventSubscriptionBuilder()
            builder.subscribe()
            val (subscription, listener) = builder.build()
            element.setSubscribedEvents(subscription.getSubscribedEventStrings())
            element.setOnEventCallback(listener)
        }
        element.setSdkAuthorization(paymentSession.getSdkAuthorization())
    }

    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        element.setConfiguration(configuration)
    }

    fun setConfiguration(configurationMap: Map<String, Any?>) {
        element.setConfiguration(ConversionUtils.convertMapToReadableMap(configurationMap))
    }

    fun onPaymentResult(listener: PaymentResultListener) {
        element.onPaymentResult(listener)
    }

    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        element.onPaymentResult(onResult)
    }

    fun onPaymentConfirmButtonClick(
        onConfirmButtonClick:
            (data: PaymentRequestData?,
             onConfirmPaymentCallback: (Boolean) -> Unit)
        -> Unit){
        element.onPaymentConfirmButtonClick(onConfirmButtonClick)
    }

    @JvmSynthetic
    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            element.confirmPayment({it -> continuation.resume(it)})
        }
    }

    fun confirmPayment(onResult: (PaymentResult) -> Unit) {
        element.confirmPayment(onResult)
    }

    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    @JvmSynthetic
    suspend fun updateIntentComplete(sdkAuthorization: String): ElementUpdateIntentResult {
        return element.updateIntentComplete(sdkAuthorization)
    }

    fun destroy() {
        element.onPaymentResult(PaymentResultListener { /* disposed - no-op */ })
    }
}