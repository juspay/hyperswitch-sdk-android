package io.hyperswitch

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Payment Event Types
 *
 * List the events to receive in the configuration's `subscriptionEvents`, then handle them
 * all in one `onChange`, switching on [PaymentEvent.eventName]. Nothing is emitted for
 * events that are not listed.
 *
 * Example usage:
 * ```
 * val configuration = PaymentSheet.Configuration.Builder("Merchant")
 *     .subscriptionEvents(listOf(PaymentEvents.CardDetailsChange, PaymentEvents.FormStatusChange))
 *     .build()
 * paymentElement.setConfiguration(configuration)
 * paymentElement.onChange { event ->
 *     when (val data = event.data) {
 *         is PaymentEventData.CardInfo -> { /* card field changes */ }
 *         is PaymentEventData.FormStatus -> { /* form completion */ }
 *         else -> {}
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
     * - extendedBin?: String            First 8 digits of card number
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
    @Parcelize
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
    @Parcelize
    object PaymentMethodChange : EventType("paymentMethodChange")

    /**
     * Form status event - emitted when form completion status changes.
     * Event type: "formStatusChange"
     * Payload: PaymentEventData.FormStatus
     *
     * Fields:
     * - status: String                  "EMPTY" | "FILLING" | "COMPLETE"
     */
    @Parcelize
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
    @Parcelize
    object BillingDetailsChange : EventType("billingDetailsChange")
}

/**
 * Base class for all payment event types.
 * Singletons ensure reference equality works correctly.
 */
sealed class EventType(val value: String) : Parcelable {
    override fun toString(): String = value
}

