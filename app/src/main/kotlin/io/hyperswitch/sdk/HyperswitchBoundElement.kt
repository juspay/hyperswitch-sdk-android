package io.hyperswitch.sdk

import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import io.hyperswitch.view.PaymentResultListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class HyperswitchBoundElement internal constructor(
    paymentSession: PaymentSession,
    private val element: HyperswitchElement,
) {
    init {
        element.initWidget(paymentSession.getPublishableKey())
        element.setSdkAuthorization(paymentSession.getSdkAuthorization())
    }

    fun subscribe(block: PaymentEventSubscriptionBuilder.() -> Unit) {
        val builder = PaymentEventSubscriptionBuilder()
        builder.block()

        val (subscription, listener) = builder.build()

        element.setSubscribedEvents(subscription.getSubscribedEventStrings())
        element.setOnEventCallback(listener)
    }

    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration){
        element.setConfiguration(configuration)
    }

//    /** RN bridge path - sets configuration using ReadableMap */
//    fun setConfiguration(configuration: ReadableMap) {
//        element.setConfiguration(configuration)
//    }

//    /** Registers a PaymentResultListener */
//    fun onPaymentResult(listener: PaymentResultListener) {
//        element.onPaymentResult(listener)
//    }

    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        element.onPaymentResult(onResult)
    }

    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult : PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmPayment(callback)
        }
    }
    fun confirmPayment(onResult : (PaymentResult) -> Unit) {
        element.confirmPayment(onResult)
    }

    suspend fun confirmCVCWidget(paymentToken: String, paymentMethodId: String): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult : PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmCVCWidget(paymentToken, paymentMethodId, callback)
        }
    }

    fun confirmCVCWidget(paymentToken: String, paymentMethodId: String, onResult : (PaymentResult) -> Unit) {
        element.confirmCVCWidget(paymentToken, paymentMethodId, onResult)
    }

    fun updateIntent(
        scope: CoroutineScope,
        sessionTokenProvider: suspend () -> String,
        onResult: (PaymentResult) -> Unit
    ) {
        element.updateIntent(scope, sessionTokenProvider, onResult)
    }
    fun updateIntent(
        sessionTokenProvider:  () -> String,
        onResult: (PaymentResult) -> Unit
    ) {
        element.updateIntent(sessionTokenProvider, onResult)
    }
}
