package io.hyperswitch.paymentsheet

import org.json.JSONObject

data class PaymentRequestData(
    val paymentMethodType: String?
) {
    companion object {
        fun parse(data: String): PaymentRequestData? {
            try {
                val jsonObject = JSONObject(data)
                return PaymentRequestData(
                    jsonObject.optString("paymentMethodType")
                )
            } catch (_: Exception) {
                return null
            }
        }
        fun toMap(data: String): Map<String, Any?> {
            try {
                val jsonObject = JSONObject(data)
                return mapOf(
                    "paymentMethodType" to jsonObject.optString("paymentMethodType")
                )
            } catch (_: Exception) {
                return emptyMap()
            }
        }
    }
}