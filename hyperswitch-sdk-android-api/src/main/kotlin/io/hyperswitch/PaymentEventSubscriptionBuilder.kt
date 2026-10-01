package io.hyperswitch


/**
 * Legacy per-event subscription. Prefer listing events in the configuration's
 * `subscriptionEvents` and handling them in the element's `onChange`.
 */
class PaymentEventSubscriptionBuilder {
    private val handlers = mutableMapOf<EventType, (PaymentEvent) -> Unit>()

    @Deprecated("List the event in configuration subscriptionEvents and handle it in onChange")
    fun on(eventType: EventType, handler: (PaymentEvent) -> Unit) {
        handlers[eventType] = handler
    }

    fun build(): Pair<PaymentEventSubscription, PaymentEventListener> {
        val subscribedEvents = handlers.keys.toList()

        val subscription = PaymentEventSubscription(
            eventTypes = subscribedEvents
        )

        // Build an O(1) lookup map keyed by event type string
        val dispatchMap: Map<String, (PaymentEvent) -> Unit> =
            handlers.entries.associate { (k, v) -> k.value to v }

        // Create dispatcher that routes events to appropriate handlers
        val listener = object : PaymentEventListener {
            override fun onPaymentEvent(event: PaymentEvent) {
                dispatchMap[event.eventName]?.invoke(event)
            }
        }

        return Pair(subscription, listener)
    }
}

/**
 * PaymentEventSubscription holds the list of subscribed event types.
 */
data class PaymentEventSubscription(
    val eventTypes: List<EventType>
) {
    /**
     * Checks if a specific event type string is subscribed.
     */
    fun isSubscribed(eventTypeString: String): Boolean {
        return eventTypes.any { it.value == eventTypeString }
    }

    fun getSubscribedEventStrings(): List<String> {
        return eventTypes.map { it.value }
    }
}