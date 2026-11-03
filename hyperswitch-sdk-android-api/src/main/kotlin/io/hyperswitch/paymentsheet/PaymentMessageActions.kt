package io.hyperswitch.paymentsheet

import androidx.annotation.RestrictTo

/** Runs the merchant callback for a tapped message action. Client-core decides what is shown. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PaymentMessageActions {
    const val EVENT_NAME = "customMessageElementClicked"

    /** Consumes internal events even after the owner releases its configuration. */
    fun dispatch(configuration: PaymentSheet.Configuration?, type: String, payload: Map<String, Any?>): Boolean {
        if (type != EVENT_NAME) return false
        val key = payload["key"] as? String ?: return true
        val element = configuration?.paymentMethodsConfig.orEmpty().flatMap { it.elements }.find { it.key == key }
        if (payload["type"] == "action") element?.onClick?.invoke()
        return true
    }
}
