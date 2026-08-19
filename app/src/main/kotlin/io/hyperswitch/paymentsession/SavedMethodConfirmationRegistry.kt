package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

typealias ConfirmationCallback = (PaymentResult) -> Unit

/** Holds only confirmations currently running for a saved-method handler. */
internal object SavedMethodConfirmationRegistry {
    private val callbacks = ConcurrentHashMap<String, ConfirmationCallback>()

    fun tryRegister(
        sdkAuthorization: String,
        callback: ConfirmationCallback,
    ): Boolean = sdkAuthorization.isNotEmpty() &&
        callbacks.putIfAbsent(sdkAuthorization, callback) == null

    fun complete(sdkAuthorization: String, data: String): Boolean {
        val callback = callbacks.remove(sdkAuthorization) ?: return false
        callback(parseResult(data))
        return true
    }

    fun remove(sdkAuthorization: String) {
        callbacks.remove(sdkAuthorization)
    }

    private fun parseResult(data: String): PaymentResult {
        val message = runCatching { JSONObject(data) }.getOrNull()
            ?: return failure("UNKNOWN_ERROR", "An error has occurred.")
        return when (val status = message.optString("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> failure(
                message.optString("code", "UNKNOWN_ERROR").ifEmpty { "UNKNOWN_ERROR" },
                message.optString("message", "An error has occurred."),
            )
            "" -> failure("UNKNOWN_ERROR", "An error has occurred.")

            else -> PaymentResult.Completed(status)
        }
    }

    private fun failure(code: String, message: String): PaymentResult.Failed =
        PaymentResult.Failed(Throwable(message).apply {
            initCause(Throwable(code))
        })
}
