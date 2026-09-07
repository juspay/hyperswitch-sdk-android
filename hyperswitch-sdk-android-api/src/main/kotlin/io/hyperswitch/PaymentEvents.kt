package io.hyperswitch

/**
 * Payment Event Types
 *
 * Type-safe event subscription system for the 4 canonical payment events.
 * Each event type corresponds directly to a structured payload in PaymentEventData.
 *
 * Example usage:
 * ```
 * paymentSession.subscribe {
 *     on(PaymentEvents.CardDetailsChange) { event ->
 *         val cardInfo = event.data as PaymentEventData.CardInfo
 *         // Handle card field changes
 *     }
 *     on(PaymentEvents.PaymentMethodChange) { event ->
 *         val status = event.data as PaymentEventData.PaymentMethodStatusEvent
 *         // Handle payment method selection
 *     }
 * }
 * ```
 */
object PaymentEvents {

    /**
     * Card information event - emitted when card field values change.
     * Event type: "cardDetailsChange"
     * Payload: PaymentEventData.CardInfo
     *
     * Fields:
     * - bin?: String                    First 6 digits of card number
     * - last4?: String                  Last 4 digits of card number
     * - brand?: String                  Card brand (Visa, Mastercard, Amex)
     * - expiryMonth?: String            Two-digit expiry month
     * - expiryYear?: String             Four-digit expiry year
     * - formattedExpiry?: String         Formatted expiry string (e.g. "01/25")
     * - isCardNumberComplete: Boolean   Card number passes length validation
     * - isCvcComplete: Boolean          CVC passes length validation
     * - isExpiryComplete: Boolean       Expiry is valid and not in the past
     * - isCardNumberValid: Boolean      Card number passes Luhn validation
     * - isExpiryValid: Boolean          Expiry date is valid
     */
    object CardDetailsChange : EventType("cardDetailsChange")

    /**
     * Payment method status event - emitted when user selects a payment method.
     * Event type: "paymentMethodChange"
     * Payload: PaymentEventData.PaymentMethodStatus
     *
     * Fields:
     * - paymentMethod: String           Payment method category (card, wallet, bank_redirect)
     * - paymentMethodType: String       Payment method sub-type (sofort, ideal)
     * - isSavedPaymentMethod: Boolean   Whether a saved payment method was selected
     * - isOneClickWallet: Boolean       Whether a one-click wallet was selected
     */
    object PaymentMethodChange : EventType("paymentMethodChange")

    /**
     * Form status event - emitted when form completion status changes.
     * Event type: "formStatusChange"
     * Payload: PaymentEventData.FormStatusEvent
     *
     * Fields:
     * - status: String                  "EMPTY" | "FILLING" | "COMPLETE"
     */
    object FormStatusChange : EventType("formStatusChange")

    /**
     * Address information event - emitted when billing address fields change.
     * Event type: "billingDetailsChange"
     * Payload: PaymentEventData.PaymentMethodInfoAddress
     *
     * Fields:
     * - country: String                 Country code
     * - state: String                   State/province
     * - postalCode: String              Postal/ZIP code
     */
    object BillingDetailsChange : EventType("billingDetailsChange")
}

/**
 * Base class for all payment event types.
 * Singletons ensure reference equality works correctly.
 */
sealed class EventType(val value: String) {
    override fun toString(): String = value
}

