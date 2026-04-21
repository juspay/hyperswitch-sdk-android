package io.hyperswitch.sdk

import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.model.ElementUpdateIntentResult
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
    configuration: PaymentSheet.Configuration? = null
) {
    init {
        element.initWidget(paymentSession.getPublishableKey())
        if(configuration != null) {
            element.setConfiguration(configuration)
        }
        element.setSdkAuthorization(paymentSession.getSdkAuthorization())
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
    fun onPaymentResult(listener: PaymentResultListener) {
        element.onPaymentResult(listener)
    }

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

    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    suspend fun updateIntentComplete(
        sdkAuthorization: String
    ): ElementUpdateIntentResult {
        return element.updateIntentComplete(sdkAuthorization)
    }
}
