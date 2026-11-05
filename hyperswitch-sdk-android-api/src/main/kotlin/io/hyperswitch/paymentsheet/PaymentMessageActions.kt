package io.hyperswitch.paymentsheet

import androidx.annotation.RestrictTo

/** Runs the merchant callback for a tapped message action. Client-core decides what is shown. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PaymentMessageActions {
    /* `index` counts actions across all messages in this order, the order they were written. */
    fun run(configuration: PaymentSheet.Configuration?, index: Int) {
        configuration?.paymentMethodsConfig.orEmpty()
            .flatMap { listOf(it.message) + it.paymentMethodTypes.orEmpty().map { type -> type.message } }
            .flatMap { it?.elements.orEmpty() }
            .getOrNull(index)
            ?.onClick
            ?.invoke()
    }
}
