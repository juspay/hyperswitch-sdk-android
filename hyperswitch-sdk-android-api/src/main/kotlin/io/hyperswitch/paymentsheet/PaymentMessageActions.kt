package io.hyperswitch.paymentsheet

import androidx.annotation.RestrictTo

/** Runs the merchant callback for a tapped message action. Client-core decides what is shown. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PaymentMessageActions {
    const val EVENT_NAME = "customMessageElementClicked"

    /** Consumes internal events even after the owner releases its configuration. */
    fun dispatch(configuration: PaymentSheet.Configuration?, type: String, payload: Map<String, Any?>): Boolean {
        if (type != EVENT_NAME) return false
        /* `index` counts actions across all messages in this order, the order they were written. */
        val elements = configuration?.paymentMethodsConfig.orEmpty()
            .flatMap { listOf(it.message) + it.paymentMethodTypes.orEmpty().map { type -> type.message } }
            .flatMap { it?.elements.orEmpty() }
        val index = (payload["index"] as? Number)?.toInt() ?: return true
        elements.getOrNull(index)?.onClick?.invoke()
        return true
    }
}
