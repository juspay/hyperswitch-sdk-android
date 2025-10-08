package io.hyperswitch.paymentsession

sealed class PaymentMethod {
    data class Card(
        val isDefaultPaymentMethod: Boolean,
        val paymentToken: String,
        val cardScheme: String,
        val name: String,
        val expiryDate: String,
        val cardNumber: String,
        val nickName: String,
        val cardHolderName: String,
        val requiresCVV: Boolean,
        val created: String,
        val lastUsedAt: String,
    ) : PaymentMethod() {
        fun toMap(): HashMap<String, Any> {
            return HashMap<String, Any>().apply {
                this["isDefaultPaymentMethod"] = isDefaultPaymentMethod
                this["paymentToken"] = paymentToken
                this["cardScheme"] = cardScheme
                this["name"] = name
                this["expiryDate"] = expiryDate
                this["cardNumber"] = cardNumber
                this["nickName"] = nickName
                this["cardHolderName"] = cardHolderName
                this["requiresCVV"] = requiresCVV
                this["created"] = created
                this["lastUsedAt"] = lastUsedAt
            }
        }
    }

    data class Wallet(
        val isDefaultPaymentMethod: Boolean,
        val paymentToken: String,
        val walletType: String,
        val created: String,
        val lastUsedAt: String,
    ) : PaymentMethod() {
        fun toMap(): HashMap<String, Any> {
            return HashMap<String, Any>().apply {
                this["isDefaultPaymentMethod"] = isDefaultPaymentMethod
                this["paymentToken"] = paymentToken
                this["walletType"] = walletType
                this["created"] = created
                this["lastUsedAt"] = lastUsedAt
            }
        }
    }

    data class Error(
        val code: String,
        val message: String,
    ) : PaymentMethod() {
        fun toMap(): HashMap<String, Any> {
            return HashMap<String, Any>().apply {
                this["code"] = code
                this["message"] = message
            }
        }
    }
}
