package io.hyperswitch.paymentmethods

import org.json.JSONObject

/**
 * Wire model of one entry of the `customer_payment_methods` array returned by the
 * customer payment methods API.
 *
 * ```json
 * {
 *   "payment_method_token": "7ebf443f-a050-4067-84e5-e6f6d4800aef",
 *   "customer_id": "0a_cus_...",
 *   "payment_method_type": "card",
 *   "payment_method_subtype": "ach",
 *   "recurring_enabled": true,
 *   "created": "2023-01-18T11:04:09.922Z",
 *   "requires_cvv": true,
 *   "last_used_at": "2024-02-24T11:04:09.922Z",
 *   "is_default": true,
 *   "payment_method_data": { "card": { ... } },
 *   "bank": { ... },
 *   "billing": { ... }
 * }
 * ```
 */
data class CustomerPaymentMethod(
    val paymentMethodToken: String,
    val customerId: String,
    val paymentMethodType: String,
    val paymentMethodSubtype: String?,
    val recurringEnabled: Boolean,
    val created: String?,
    val requiresCvv: Boolean,
    val lastUsedAt: String?,
    val isDefault: Boolean,
    val paymentMethodData: PaymentMethodData?,
    val bank: BankData?,
    val billing: BillingData?,
) {

    /** `payment_method_data` wrapper — currently only `card` payloads. */
    data class PaymentMethodData(
        val card: CardData?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): PaymentMethodData = PaymentMethodData(
                card = json.optJSONObject("card")?.let(CardData::fromJson),
            )
        }
    }

    /** Card details nested inside `payment_method_data.card`. */
    data class CardData(
        val savedToLocker: Boolean,
        val issuerCountry: String?,
        val last4Digits: String?,
        val expiryMonth: String?,
        val expiryYear: String?,
        val cardHolderName: String?,
        val cardFingerprint: String?,
        val nickName: String?,
        val cardNetwork: String?,
        val cardIsin: String?,
        val cardIssuer: String?,
        val cardType: String?,
        val cardSubtype: String?,
        val cardSegmentType: String?,
        val fundingSource: String?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): CardData = CardData(
                savedToLocker = json.optBoolean("saved_to_locker"),
                issuerCountry = json.optStringOrNull("issuer_country"),
                last4Digits = json.optStringOrNull("last4_digits"),
                expiryMonth = json.optStringOrNull("expiry_month"),
                expiryYear = json.optStringOrNull("expiry_year"),
                cardHolderName = json.optStringOrNull("card_holder_name"),
                cardFingerprint = json.optStringOrNull("card_fingerprint"),
                nickName = json.optStringOrNull("nick_name"),
                cardNetwork = json.optStringOrNull("card_network"),
                cardIsin = json.optStringOrNull("card_isin"),
                cardIssuer = json.optStringOrNull("card_issuer"),
                cardType = json.optStringOrNull("card_type"),
                cardSubtype = json.optStringOrNull("card_subtype"),
                cardSegmentType = json.optStringOrNull("card_segment_type"),
                fundingSource = json.optStringOrNull("funding_source"),
            )
        }
    }

    /** Bank account details (top-level sibling of `payment_method_data`). */
    data class BankData(
        val mask: String?,
        val accountHolderName: String?,
        val bankName: String?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): BankData = BankData(
                mask = json.optStringOrNull("mask"),
                accountHolderName = json.optStringOrNull("account_holder_name"),
                bankName = json.optStringOrNull("bank_name"),
            )
        }
    }

    /** Billing block (top-level sibling of `payment_method_data`). */
    data class BillingData(
        val address: AddressData?,
        val phone: PhoneData?,
        val email: String?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): BillingData = BillingData(
                address = json.optJSONObject("address")?.let(AddressData::fromJson),
                phone = json.optJSONObject("phone")?.let(PhoneData::fromJson),
                email = json.optStringOrNull("email"),
            )
        }
    }

    data class AddressData(
        val city: String?,
        val country: String?,
        val line1: String?,
        val line2: String?,
        val line3: String?,
        val zip: String?,
        val state: String?,
        val firstName: String?,
        val lastName: String?,
        val originZip: String?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): AddressData = AddressData(
                city = json.optStringOrNull("city"),
                country = json.optStringOrNull("country"),
                line1 = json.optStringOrNull("line1"),
                line2 = json.optStringOrNull("line2"),
                line3 = json.optStringOrNull("line3"),
                zip = json.optStringOrNull("zip"),
                state = json.optStringOrNull("state"),
                firstName = json.optStringOrNull("first_name"),
                lastName = json.optStringOrNull("last_name"),
                originZip = json.optStringOrNull("origin_zip"),
            )
        }
    }

    data class PhoneData(
        val number: String?,
        val countryCode: String?,
    ) {
        companion object {
            fun fromJson(json: JSONObject): PhoneData = PhoneData(
                number = json.optStringOrNull("number"),
                countryCode = json.optStringOrNull("country_code"),
            )
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomerPaymentMethod = CustomerPaymentMethod(
            paymentMethodToken = json.getString("payment_method_token"),
            customerId = json.getString("customer_id"),
            paymentMethodType = json.getString("payment_method_type"),
            paymentMethodSubtype = json.optStringOrNull("payment_method_subtype"),
            recurringEnabled = json.optBoolean("recurring_enabled"),
            created = json.optStringOrNull("created"),
            requiresCvv = json.optBoolean("requires_cvv"),
            lastUsedAt = json.optStringOrNull("last_used_at"),
            isDefault = json.optBoolean("is_default"),
            paymentMethodData = json.optJSONObject("payment_method_data")?.let(PaymentMethodData::fromJson),
            bank = json.optJSONObject("bank")?.let(BankData::fromJson),
            billing = json.optJSONObject("billing")?.let(BillingData::fromJson),
        )

        /** Empty-string-safe variant of [JSONObject.optString] — null when absent or "". */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key)
    }
}
