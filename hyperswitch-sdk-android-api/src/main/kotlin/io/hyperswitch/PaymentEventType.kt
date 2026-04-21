package io.hyperswitch

interface PaymentEventListener {
    fun onPaymentEvent(event: PaymentEvent)
}

data class PaymentEvent(
    val type: String,
    val payload: Map<String, Any>,
) {
    val data: PaymentEventData? by lazy { PaymentEventData.fromEventType(type, payload) }
}