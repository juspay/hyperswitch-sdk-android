package io.hyperswitch.sdk

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsession.Callback
import io.hyperswitch.paymentsession.PMError
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
    // private var lastUsedPaymentToken: String? = null
    private var lastUsedBilling: String? = null
    // private var defaultPaymentToken: String? = null
    private var defaultBilling: String? = null

    init {
        element.initWidget(paymentSession.getPublishableKey())
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

    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        element.setConfiguration(configuration)
    }

    /** Map path - sets configuration using a raw configurationMap (mirrors presentPaymentSheet overload) */
    fun setConfiguration(configurationMap: Map<String, Any?>) {
        element.setConfiguration(ConversionUtils.convertMapToReadableMap(configurationMap))
    }

//    /** RN bridge path - sets configuration using ReadableMap */
//    fun setConfiguration(configuration: ReadableMap) {
//        element.setConfiguration(configuration)
//    }

    //    /** Registers a PaymentResultListener */
    fun onPaymentResult(listener: PaymentResultListener) {
        element.onPaymentResult(listener)
    }

    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        element.onPaymentResult(onResult)
    }

    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult: PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmPayment(callback)
        }
    }

    fun confirmPayment(onResult: (PaymentResult) -> Unit) {
        element.confirmPayment(onResult)
    }



    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    suspend fun updateIntentComplete(sdkAuthorization: String): ElementUpdateIntentResult {
        // Update the element's SDK authorization to ensure all subsequent operations use the new auth
        element.setSdkAuthorization(sdkAuthorization)
        return element.updateIntentComplete(sdkAuthorization)
    }
}
