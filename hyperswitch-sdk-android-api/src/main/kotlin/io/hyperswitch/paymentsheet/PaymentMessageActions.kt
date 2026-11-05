package io.hyperswitch.paymentsheet

import androidx.annotation.RestrictTo

/** Runs the merchant callback for a tapped message action. Client-core decides what is shown. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PaymentMessageActions {
    /* `paymentMethodType` is "" when the shown text is the payment method's own message; `index`
       is the segment's position in that message's `value`. `text` is what was shown there: a
       segment with other text is not the one tapped, so nothing runs. */
    fun run(
        configuration: PaymentSheet.Configuration?, paymentMethod: String, paymentMethodType: String, index: Int, text: String
    ) {
        val config = configuration?.paymentMethodsConfig?.find { it.paymentMethod.value == paymentMethod } ?: return
        val message = if (paymentMethodType.isEmpty()) {
            config.message
        } else {
            config.paymentMethodTypes?.find { it.paymentMethodType.value == paymentMethodType }?.message
        } ?: return
        val (label, element) = PaymentMessageWire.segments(message).getOrNull(index) ?: return
        if (label == text) element?.onClick?.invoke()
    }
}
