package io.hyperswitch.react

import io.hyperswitch.paymentsheet.PaymentResult
import org.json.JSONObject

/** Decodes the `{status, code, message}` JSON every JS exit call sends. */
internal fun parsePaymentResult(json: String): PaymentResult {
    val obj = try {
        JSONObject(json)
    } catch (_: Exception) {
        return PaymentResult.Failed(Throwable("Invalid result").apply { initCause(Throwable("UNKNOWN_ERROR")) })
    }
    return when (val status = obj.optString("status")) {
        "cancelled" -> PaymentResult.Canceled(status)
        "failed", "requires_payment_method", "form_invalid" -> {
            val throwable = Throwable(obj.optString("message").ifEmpty { status })
            throwable.initCause(Throwable(obj.optString("code")))
            PaymentResult.Failed(throwable)
        }
        else -> PaymentResult.Completed(status.ifEmpty { "default" })
    }
}
