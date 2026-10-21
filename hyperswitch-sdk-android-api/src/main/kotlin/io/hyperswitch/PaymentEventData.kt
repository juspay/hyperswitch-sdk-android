package io.hyperswitch

/**
 * Typed payload hierarchy for payment events.
 *
 * Each subclass mirrors the corresponding RN-side typed record in PaymentEvents.res exactly.
 * This 1:1 contract means merchants receive structured, type-safe data instead of raw maps.
 */
sealed class PaymentEventData() {

    /** JSON representation used by wrapper bridges. */
    abstract fun toMap(): Map<String, Any?>

    /**
     * Card information event payload.
     * Matches the canonical cardDetailsChange structure exactly.
     *
     * @property bin                  First 6 digits of the card number, or null if fewer than 6 entered
     * @property extendedBin          First 8 digits of the card number, or null if fewer than 8 entered
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
    data class CardDetailsChange(
        val bin: String?,
        val extendedBin: String?,
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
            fun fromMap(map: Map<String, Any?>): CardDetailsChange {
                val reader = PayloadReader(map)
                return CardDetailsChange(
                    bin = reader.nullableString("bin"),
                    extendedBin = reader.nullableString("extendedBin"),
                    last4 = reader.nullableString("last4"),
                    brand = reader.nullableString("brand"),
                    expiryMonth = reader.nullableString("expiryMonth"),
                    expiryYear = reader.nullableString("expiryYear"),
                    formattedExpiry = reader.nullableString("formattedExpiry"),
                    isCardNumberComplete = reader.boolean("isCardNumberComplete"),
                    isCvcComplete = reader.boolean("isCvcComplete"),
                    isExpiryComplete = reader.boolean("isExpiryComplete"),
                    isCardNumberValid = reader.boolean("isCardNumberValid"),
                    isExpiryValid = reader.boolean("isExpiryValid"),
                )
            }
        }

        override fun toMap(): Map<String, Any?> = mapOf(
            "bin" to bin,
            "extendedBin" to extendedBin,
            "last4" to last4,
            "brand" to brand,
            "expiryMonth" to expiryMonth,
            "expiryYear" to expiryYear,
            "formattedExpiry" to formattedExpiry,
            "isCardNumberComplete" to isCardNumberComplete,
            "isCardNumberValid" to isCardNumberValid,
            "isExpiryComplete" to isExpiryComplete,
            "isExpiryValid" to isExpiryValid,
            "isCvcComplete" to isCvcComplete,
        )
    }

    /**
     * Payment method status event payload.
     * Matches the canonical paymentMethodChange structure exactly.
     *
     * @property paymentMethod        Payment method category (e.g., "card", "wallet", "bank_redirect")
     * @property paymentMethodType    Payment method sub-type (e.g., "sofort", "ideal")
     * @property isSavedPaymentMethod Whether a saved payment method was selected
     * @property isOneClickWallet     Whether a one-click wallet was selected
     */
    data class PaymentMethodChange(
        val paymentMethod: String,
        val paymentMethodType: String,
        val isSavedPaymentMethod: Boolean,
        val isOneClickWallet: Boolean,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any?>): PaymentMethodChange {
                val reader = PayloadReader(map)
                return PaymentMethodChange(
                    paymentMethod = reader.string("paymentMethod"),
                    paymentMethodType = reader.string("paymentMethodType"),
                    isSavedPaymentMethod = reader.boolean("isSavedPaymentMethod"),
                    isOneClickWallet = reader.boolean("isOneClickWallet"),
                )
            }
        }

        override fun toMap(): Map<String, Any?> = mapOf(
            "paymentMethod" to paymentMethod,
            "paymentMethodType" to paymentMethodType,
            "isSavedPaymentMethod" to isSavedPaymentMethod,
            "isOneClickWallet" to isOneClickWallet,
        )
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
     * @property status Form completion status
     */
    data class FormStatusChange(
        val status: FormStatusValue,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any?>): FormStatusChange {
                val reader = PayloadReader(map)
                return FormStatusChange(
                    status = requireNotNull(FormStatusValue.fromString(reader.string("status"))),
                )
            }
        }

        override fun toMap(): Map<String, Any?> = mapOf(
            "status" to status.name,
        )
    }

    /**
     * Address information event payload.
     * Matches the canonical billingDetailsChange structure exactly.
     *
     * @property country    Country code
     * @property state      State/province
     * @property postalCode Postal/ZIP code
     */
    data class BillingDetailsChange(
        val country: String?,
        val state: String?,
        val postalCode: String?,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any?>): BillingDetailsChange {
                val reader = PayloadReader(map)
                return BillingDetailsChange(
                    country = reader.nullableString("country"),
                    state = reader.nullableString("state"),
                    postalCode = reader.nullableString("postalCode"),
                )
            }
        }

        override fun toMap(): Map<String, Any?> = mapOf(
            "country" to country,
            "state" to state,
            "postalCode" to postalCode,
        )
    }

    /**
     * CVC status event payload.
     *
     * @property cvcStatus Empty/complete flags, nested as in the emitted payload
     */
    data class CvcStatusChange(
        val cvcStatus: CvcStatusDetails,
    ) : PaymentEventData() {

        companion object {
            fun fromMap(map: Map<String, Any?>): CvcStatusChange {
                val reader = PayloadReader(map)
                return CvcStatusChange(
                    cvcStatus = reader.cvcStatus(),
                )
            }
        }

        override fun toMap(): Map<String, Any?> = mapOf(
            "cvcStatus" to mapOf("isCvcEmpty" to cvcStatus.isCvcEmpty, "isCvcComplete" to cvcStatus.isCvcComplete),
        )
    }

    data class CvcStatusDetails(val isCvcEmpty: Boolean, val isCvcComplete: Boolean)

    companion object {
        /** Decode the payload for its event name; malformed payloads never reach onChange. */
        fun fromEventType(eventType: EventType, payload: Map<String, Any?>): PaymentEventData? =
            try {
                when (eventType) {
                    PaymentEvents.CardDetailsChange -> CardDetailsChange.fromMap(payload)
                    PaymentEvents.PaymentMethodChange -> PaymentMethodChange.fromMap(payload)
                    PaymentEvents.FormStatusChange -> FormStatusChange.fromMap(payload)
                    PaymentEvents.BillingDetailsChange -> BillingDetailsChange.fromMap(payload)
                    CvcWidgetEvents.CvcStatusChange -> CvcStatusChange.fromMap(payload)
                }
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

private class PayloadReader(private val values: Map<String, Any?>) {
    fun nullableString(key: String): String? {
        require(values.containsKey(key))
        val value = values[key]
        require(value == null || value is String)
        return value as String?
    }

    fun string(key: String): String = requireNotNull(values[key] as? String)
    fun boolean(key: String): Boolean = requireNotNull(values[key] as? Boolean)

    fun cvcStatus(): PaymentEventData.CvcStatusDetails {
        val status = requireNotNull(values["cvcStatus"] as? Map<*, *>)
        return PaymentEventData.CvcStatusDetails(
            isCvcEmpty = requireNotNull(status["isCvcEmpty"] as? Boolean),
            isCvcComplete = requireNotNull(status["isCvcComplete"] as? Boolean),
        )
    }
}
