package io.hyperswitch.paymentsheet

/**
 * Callback that is invoked when a [PaymentResult] is available.
 */
fun interface PaymentResultCallback {
    fun onPaymentResult(PaymentResult: PaymentResult)
}