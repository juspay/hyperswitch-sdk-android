package io.hyperswitch.sdk

import io.hyperswitch.paymentsheet.PaymentResult
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
            val callback = { paymentResult : PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmPayment(callback)
        }
    }
}