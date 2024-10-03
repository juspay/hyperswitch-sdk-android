package io.hyperswitch.lite.payments.paymentlauncher

fun interface PaymentResultCallback {
    fun onPaymentResult(paymentResult: PaymentResult)
}
