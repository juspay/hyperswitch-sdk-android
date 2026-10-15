package io.hyperswitch

fun interface PaymentEventListener {
    fun onPaymentEvent(event: PaymentEvent)
}

/**
 * An event delivered to an `onChange` handler. [eventName] is one of the names listed in
 * `subscriptionEvents` (e.g. "cardDetailsChange"); [data] is the typed view of [payload].
 */
data class PaymentEvent(
    val eventName: String,
    val payload: Map<String, Any?>,
) {
    val data: PaymentEventData? by lazy { PaymentEventData.fromEventType(eventName, payload) }
}
