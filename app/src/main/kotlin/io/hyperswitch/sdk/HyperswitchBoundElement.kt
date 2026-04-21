package io.hyperswitch.sdk

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import io.hyperswitch.view.PaymentResultListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class HyperswitchBoundElement internal constructor(
    paymentSession: PaymentSession,
    private val element: HyperswitchElement,
    configuration: PaymentSheet.Configuration? = null,
    subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
) {
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

    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        element.setConfiguration(configuration)
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

    suspend fun confirmCVCWidget(paymentToken: String, paymentMethodId: String): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult: PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmCVCWidget(paymentToken, paymentMethodId, callback)
        }
    }

    fun confirmCVCWidget(paymentToken: String, paymentMethodId: String, onResult: (PaymentResult) -> Unit) {
        element.confirmCVCWidget(paymentToken, paymentMethodId, onResult)
    }

    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    suspend fun updateIntentComplete(sdkAuthorization: String): ElementUpdateIntentResult {
        return element.updateIntentComplete(sdkAuthorization)
    }
}
