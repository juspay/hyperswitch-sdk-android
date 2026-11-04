package io.hyperswitch.paymentsheet

import androidx.annotation.RestrictTo

/** Runs the merchant callback for a tapped message action. Client-core decides what is shown. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object PaymentMessageActions {
    const val EVENT_NAME = "customMessageElementClicked"

    /** Consumes internal events even after the owner releases its configuration. */
    fun dispatch(configuration: PaymentSheet.Configuration?, type: String, payload: Map<String, Any?>): Boolean {
        if (type != EVENT_NAME) return false
        /* The key is `id_index`: the message's random id, then the action's index in it. */
        val parts = (payload["key"] as? String)?.split("_")
        val index = parts?.getOrNull(1)?.toIntOrNull()
        if (payload["type"] != "action" || parts?.size != 2 || index == null) return true
        val message = configuration?.paymentMethodsConfig.orEmpty()
            .flatMap { config -> listOf(config.message) + config.paymentMethodTypes.orEmpty().map { it.message } }
            .find { it?.id == parts[0] }
        message?.elements?.getOrNull(index)?.onClick?.invoke()
        return true
    }
}
