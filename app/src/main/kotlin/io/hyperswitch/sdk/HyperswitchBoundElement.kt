package io.hyperswitch.sdk

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsession.PaymentSessionHandler
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

    fun confirmWithLastUsed(handler: PaymentSessionHandler, callback: (PaymentResult) -> Unit) {
        handler.getCustomerLastUsedPaymentMethodData().fold(
            onSuccess = { data ->
                element.confirmCVCWidget(paymentSession.getSdkAuthorization(), data.paymentToken, data.billing, callback)
            },
            onFailure = { callback(PaymentResult.Failed(Exception("No last used payment details found"))) }
        )
    }

    suspend fun confirmWithLastUsed(handler: PaymentSessionHandler): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            confirmWithLastUsed(handler) { continuation.resume(it) }
        }
    }

    fun confirmWithDefaultMethod(handler: PaymentSessionHandler, callback: (PaymentResult) -> Unit) {
        handler.getCustomerDefaultSavedPaymentMethodData().fold(
            onSuccess = { data ->
                element.confirmCVCWidget(paymentSession.getSdkAuthorization(), data.paymentToken, data.billing, callback)
            },
            onFailure = { callback(PaymentResult.Failed(Exception("No default payment method found"))) }
        )
    }

    suspend fun confirmWithDefaultMethod(handler: PaymentSessionHandler): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            confirmWithDefaultMethod(handler) { continuation.resume(it) }
        }
    }

    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    suspend fun updateIntentComplete(sdkAuthorization: String): ElementUpdateIntentResult {
        return element.updateIntentComplete(sdkAuthorization)
    }
}
