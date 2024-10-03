package io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet

/**
 * Callback that is invoked when a [PaymentSheetResult] is available.
 */
fun interface PaymentSheetResultCallback {
    fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult)
}