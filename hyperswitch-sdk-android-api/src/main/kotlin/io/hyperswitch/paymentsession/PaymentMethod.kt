package io.hyperswitch.paymentsession

/**
 * Sealed class representing different payment method types
 */
sealed class PaymentMethodType {
    data object WALLET : PaymentMethodType()
    data object CARD : PaymentMethodType()
    data object CARD_REDIRECT : PaymentMethodType()
    data object PAY_LATER : PaymentMethodType()
    data object BANK_REDIRECT : PaymentMethodType()
    data object OPEN_BANKING : PaymentMethodType()
    data object BANK_DEBIT : PaymentMethodType()
    data object BANK_TRANSFER : PaymentMethodType()
    data object CRYPTO : PaymentMethodType()
    data object REWARD : PaymentMethodType()
    data object GIFT_CARD : PaymentMethodType()
    data class OTHERS(val value: String) : PaymentMethodType()

    companion object {
        /**
         * Converts a string value to the corresponding PaymentMethodType
         * @param value The string representation of the payment method
         * @return The corresponding PaymentMethodType, returns OTHERS(value) for unrecognized types
         */
        fun fromString(value: String): PaymentMethodType {
            return when (value.lowercase()) {
                "wallet" -> WALLET
                "card" -> CARD
                "card_redirect" -> CARD_REDIRECT
                "pay_later" -> PAY_LATER
                "bank_redirect" -> BANK_REDIRECT
                "open_banking" -> OPEN_BANKING
                "bank_debit" -> BANK_DEBIT
                "bank_transfer" -> BANK_TRANSFER
                "crypto" -> CRYPTO
                "reward" -> REWARD
                "gift_card" -> GIFT_CARD
                else -> OTHERS(value)
            }
        }
    }

    /**
     * Converts the PaymentMethodType to its string representation
     * @return The string representation of the payment method type
     */
    fun toStringValue(): String {
        return when (this) {
            is WALLET -> "wallet"
            is CARD -> "card"
            is CARD_REDIRECT -> "card_redirect"
            is PAY_LATER -> "pay_later"
            is BANK_REDIRECT -> "bank_redirect"
            is OPEN_BANKING -> "open_banking"
            is BANK_DEBIT -> "bank_debit"
            is BANK_TRANSFER -> "bank_transfer"
            is CRYPTO -> "crypto"
            is REWARD -> "reward"
            is GIFT_CARD -> "gift_card"
            is OTHERS -> this.value
        }
    }
}

/**
 * Represents card payment method details
 */
data class Card(
    val scheme: String,
    val issuerCountry: String,
    val last4Digits: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cardToken: String?,
    val cardHolderName: String,
    val cardFingerprint: String?,
    val nickName: String,
    val cardNetwork: String,
    val cardIsin: String,
    val cardIssuer: String,
    val cardType: String,
    val savedToLocker: Boolean,
) {
    fun toMap(): HashMap<String, Any?> {
        return HashMap<String, Any?>().apply {
            this["scheme"] = scheme
            this["issuer_country"] = issuerCountry
            this["last4_digits"] = last4Digits
            this["expiry_month"] = expiryMonth
            this["expiry_year"] = expiryYear
            this["card_token"] = cardToken
            this["card_holder_name"] = cardHolderName
            this["card_fingerprint"] = cardFingerprint
            this["nick_name"] = nickName
            this["card_network"] = cardNetwork
            this["card_isin"] = cardIsin
            this["card_issuer"] = cardIssuer
            this["card_type"] = cardType
            this["saved_to_locker"] = savedToLocker
        }
    }
}

/**
 * Represents a valid payment method with all details
 */
data class PaymentMethod(
    val paymentToken: String,
    val paymentMethodId: String,
    val customerId: String,
    val paymentMethod: PaymentMethodType,
    val paymentMethodType: String,
    val paymentMethodIssuer: String,
    val paymentMethodIssuerCode: String?,
    val recurringEnabled: Boolean,
    val installmentPaymentEnabled: Boolean,
    val paymentExperience: List<String>,
    val card: Card?,
    val metadata: String?,
    val created: String,
    val bank: String?,
    val surchargeDetails: String?,
    val requiresCvv: Boolean,
    val lastUsedAt: String,
    val defaultPaymentMethodSet: Boolean,
) {
    fun toMap(): HashMap<String, Any?> {
        return HashMap<String, Any?>().apply {
            this["payment_token"] = paymentToken
            this["payment_method_id"] = paymentMethodId
            this["customer_id"] = customerId
            this["payment_method"] = paymentMethod.toStringValue()
            this["payment_method_type"] = paymentMethodType
            this["payment_method_issuer"] = paymentMethodIssuer
            this["payment_method_issuer_code"] = paymentMethodIssuerCode
            this["recurring_enabled"] = recurringEnabled
            this["installment_payment_enabled"] = installmentPaymentEnabled
            this["payment_experience"] = paymentExperience
            this["card"] = card?.toMap()
            this["metadata"] = metadata
            this["created"] = created
            this["bank"] = bank
            this["surcharge_details"] = surchargeDetails
            this["requires_cvv"] = requiresCvv
            this["last_used_at"] = lastUsedAt
            this["default_payment_method_set"] = defaultPaymentMethodSet
        }
    }
}

/**
 * Represents an error state in payment method retrieval
 */
data class PMError(
    val code: String,
    override val message: String,
) : Throwable(message) {
    fun toMap(): HashMap<String, Any> {
        return HashMap<String, Any>().apply {
            this["code"] = code
            this["message"] = message
        }
    }
}
