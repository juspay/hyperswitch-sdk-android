package io.hyperswitch.paymentsession

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
    val paymentMethod: String,
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
            this["payment_method"] = paymentMethod
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
