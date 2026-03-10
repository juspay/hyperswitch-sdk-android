package io.hyperswitch

interface PaymentEventListener {
    /**
     * Called when a subscribed payment event occurs
     * @param event The payment event data
     */
    fun onPaymentEvent(event: PaymentEvent)
}

/**
 * Represents a payment event with its type, element context, and payload data.
 *
 * @property type        The event type identifier (e.g., "PAYMENT_METHOD_INFO_CARD")
 * @property elementType The UI element that triggered the event (e.g., "payment")
 * @property payload     The raw event data as a map (kept for backward compatibility)
 *                       Cast to the appropriate [PaymentEventData] subclass.
 */
data class PaymentEvent(
    val type: String,
    val elementType: String,
    val payload: Map<String, Any>,
) {
    val data: PaymentEventData? by lazy { PaymentEventData.fromEventType(type, payload) }
}