package io.hyperswitch

fun interface PaymentEventListener {
    fun onPaymentEvent(event: PaymentEvent)
}

/**
 * An event delivered to `onChange`. Match on [payload] for typed fields; [eventName]
 * uses the same identifiers as `subscriptionEvents` and is derived from the payload.
 */
data class PaymentEvent(
    val payload: PaymentEventData,
) {
    val eventName: EventType get() = when (payload) {
        is PaymentEventData.CardDetailsChange -> PaymentEvents.CardDetailsChange
        is PaymentEventData.PaymentMethodChange -> PaymentEvents.PaymentMethodChange
        is PaymentEventData.FormStatusChange -> PaymentEvents.FormStatusChange
        is PaymentEventData.BillingDetailsChange -> PaymentEvents.BillingDetailsChange
        is PaymentEventData.CvcStatusChange -> CvcWidgetEvents.CvcStatusChange
    }

    companion object {
        fun fromMap(eventName: EventType, payload: Map<String, Any?>): PaymentEvent? =
            PaymentEventData.fromEventType(eventName, payload)?.let(::PaymentEvent)
    }
}
