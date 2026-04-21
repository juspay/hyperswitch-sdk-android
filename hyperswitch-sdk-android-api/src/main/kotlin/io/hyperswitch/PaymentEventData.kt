package io.hyperswitch

/**
 * Typed payload hierarchy for payment events.
 *
 * Each subclass mirrors the corresponding RN-side typed record in PaymentEvents.res exactly.
 * This 1:1 contract means merchants receive structured, type-safe data instead of raw maps.
 */
sealed class PaymentEventData() {

    /**
     * Card information event payload.
     * Matches the canonical PAYMENT_METHOD_INFO_CARD structure exactly.
     *
     * @property bin                  First 6 digits of the card number, or null if fewer than 6 entered
     * @property last4                Last 4 digits of the card number, or null if fewer than 4 entered
     * @property brand                Card network brand (e.g. "Visa", "Mastercard"), or null if unknown
     * @property expiryMonth          Two-digit expiry month (e.g. "01"), or null if not entered
     * @property expiryYear           Four-digit expiry year (e.g. "2025"), or null if not entered
     * @property formattedExpiry      Formatted expiry string (e.g. "01/25"), or null if not complete
     * @property isCardNumberComplete Whether the card number passes length validation
     * @property isCvcComplete        Whether the CVC passes length validation for the given brand
     * @property isExpiryComplete     Whether the expiry is valid and not in the past
     * @property isCardNumberValid    Whether the card number passes Luhn validation
     * @property isExpiryValid        Whether the expiry date is valid
     */
    data class CardInfo(
        val bin: String?,
        val last4: String?,
        val brand: String?,
        val expiryMonth: String?,
        val expiryYear: String?,
        val formattedExpiry: String?,
        val isCardNumberComplete: Boolean,
        val isCvcComplete: Boolean,
        val isExpiryComplete: Boolean,
        val isCardNumberValid: Boolean,
        val isExpiryValid: Boolean,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any>): CardInfo = CardInfo(
                bin = map["bin"] as? String,
                last4 = map["last4"] as? String,
                brand = map["brand"] as? String,
                expiryMonth = map["expiryMonth"] as? String,
                expiryYear = map["expiryYear"] as? String,
                formattedExpiry = map["formattedExpiry"] as? String,
                isCardNumberComplete = (map["isCardNumberComplete"] as? Boolean) ?: false,
                isCvcComplete = (map["isCvcComplete"] as? Boolean) ?: false,
                isExpiryComplete = (map["isExpiryComplete"] as? Boolean) ?: false,
                isCardNumberValid = (map["isCardNumberValid"] as? Boolean) ?: false,
                isExpiryValid = (map["isExpiryValid"] as? Boolean) ?: false,
            )
        }
    }

    /**
     * Payment method status event payload.
     * Matches the canonical PAYMENT_METHOD_STATUS structure exactly.
     *
     * @property paymentMethod        Payment method category (e.g., "card", "wallet", "bank_redirect")
     * @property paymentMethodType    Payment method sub-type (e.g., "sofort", "ideal")
     * @property isSavedPaymentMethod Whether a saved payment method was selected
     * @property isOneClickWallet     Whether a one-click wallet was selected
     */
    data class PaymentMethodStatus(
        val paymentMethod: String,
        val paymentMethodType: String,
        val isSavedPaymentMethod: Boolean,
        val isOneClickWallet: Boolean,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any>): PaymentMethodStatus = PaymentMethodStatus(
                paymentMethod = (map["paymentMethod"] as? String).orEmpty(),
                paymentMethodType = (map["paymentMethodType"] as? String).orEmpty(),
                isSavedPaymentMethod = (map["isSavedPaymentMethod"] as? Boolean) ?: false,
                isOneClickWallet = (map["isOneClickWallet"] as? Boolean) ?: false,
            )
        }
    }

    /**
     * Form status values for type-safe status handling.
     */
    enum class FormStatusValue {
        EMPTY,
        FILLING,
        COMPLETE;

        companion object {
            fun fromString(value: String): FormStatusValue? =
                FormStatusValue.entries.find { it.name == value }
        }
    }

    /**
     * Form status event payload.
     *
     * @property status Form status, or null for unknown values
     */
    data class FormStatus(
        val status: FormStatusValue?,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any>): FormStatus = FormStatus(
                status = (map["status"] as? String)?.let { FormStatusValue.fromString(it) }
            )
        }
    }

    /**
     * Address information event payload.
     * Matches the canonical PAYMENT_METHOD_INFO_BILLING_ADDRESS structure exactly.
     *
     * @property country    Country code
     * @property state      State/province
     * @property postalCode Postal/ZIP code
     */
    data class PaymentMethodInfoAddress(
        val country: String,
        val state: String,
        val postalCode: String,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any>): PaymentMethodInfoAddress = PaymentMethodInfoAddress(
                country = (map["country"] as? String).orEmpty(),
                state = (map["state"] as? String).orEmpty(),
                postalCode = (map["postalCode"] as? String).orEmpty(),
            )
        }
    }

    /**
     * CVC status event payload.
     *
     * @property isCvcFocused Whether the CVC field is currently focused
     * @property isCvcBlur    Whether the CVC field has lost focus
     * @property isCvcEmpty   Whether the CVC field is empty
     */
    data class CvcStatus(
        val isCvcFocused: Boolean,
        val isCvcBlur: Boolean,
        val isCvcEmpty: Boolean,
    ) : PaymentEventData() {

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromMap(map: Map<String, Any>): CvcStatus {
                val nested = map["cvcStatus"] as? Map<String, Any> ?: map
                return CvcStatus(
                    isCvcFocused = (nested["isCvcFocused"] as? Boolean) ?: false,
                    isCvcBlur = (nested["isCvcBlur"] as? Boolean) ?: false,
                    isCvcEmpty = (nested["isCvcEmpty"] as? Boolean) ?: true,
                )
            }
        }
    }

    companion object {
        /**
         * Parse a [PaymentEventData] subclass from a raw event-type string and payload map.
         * Returns null for unrecognised event types (forward-compatibility).
         *
         * This is called automatically when constructing a [PaymentEvent] — consumers never
         * need to call this directly.
         */
        fun fromEventType(eventType: String, payload: Map<String, Any>): PaymentEventData? =
            when (eventType) {
                "PAYMENT_METHOD_INFO_CARD" -> CardInfo.fromMap(payload)
                "PAYMENT_METHOD_STATUS" -> PaymentMethodStatus.fromMap(payload)
                "FORM_STATUS" -> FormStatus.fromMap(payload)
                "PAYMENT_METHOD_INFO_BILLING_ADDRESS" -> PaymentMethodInfoAddress.fromMap(payload)
                "CVC_STATUS" -> CvcStatus.fromMap(payload)
                else -> null
            }
    }
}