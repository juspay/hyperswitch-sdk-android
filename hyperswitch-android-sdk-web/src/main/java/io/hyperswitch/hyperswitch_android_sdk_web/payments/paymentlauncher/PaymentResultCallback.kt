package io.hyperswitch.hyperswitch_android_sdk_web.payments.paymentlauncher

fun interface PaymentResultCallback {
    fun onPaymentResult(paymentResult: PaymentResult)
}
