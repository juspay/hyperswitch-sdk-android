package io.hyperswitch.sdk

import com.facebook.react.bridge.Callback
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.view.HyperswitchElement
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class HyperswitchBoundElement internal constructor(
    private val paymentSession: PaymentSession,
    private val element: HyperswitchElement,
) {
    init {
        element.initWidget(paymentSession.getPublishableKey())
        element.setSdkAuthorization(paymentSession.getSdkAuthorization())
    }

    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = Callback { args ->
                val result = PaymentResult.Completed("success")
                continuation.resume(result)
            }
            element.confirmPayment(callback)
        }
    }
}