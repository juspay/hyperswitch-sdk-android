package io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet

import io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet.PaymentOption

/**
 * Callback that is invoked when the customer's [PaymentOption] selection changes.
 */
fun interface PaymentOptionCallback {

    /**
     * @param paymentOption The new [PaymentOption] selection. If null, the customer has not yet
     * selected a [PaymentOption]. The customer can only complete the transaction if this value is
     * not null.
     */
    fun onPaymentOption(paymentOption: PaymentOption?)
}
