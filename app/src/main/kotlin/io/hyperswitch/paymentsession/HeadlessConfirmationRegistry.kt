package io.hyperswitch.paymentsession

import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap

typealias ConfirmationCallback = (PaymentResult) -> Unit

/** Holds only confirmations currently running through the headless runtime. */
internal object HeadlessConfirmationRegistry {
    private val callbacks = ConcurrentHashMap<String, ConfirmationCallback>()

    fun tryRegister(
        sdkAuthorization: String,
        callback: ConfirmationCallback,
    ): Boolean = sdkAuthorization.isNotEmpty() &&
        callbacks.putIfAbsent(sdkAuthorization, callback) == null

    fun complete(sdkAuthorization: String, result: ReadableMap): Boolean {
        val callback = callbacks.remove(sdkAuthorization) ?: return false
        callback(parseResult(result))
        return true
    }

    fun remove(sdkAuthorization: String) {
        callbacks.remove(sdkAuthorization)
    }

    // `result` is the codegen PaymentExitResult object: {status, type?, code?, message?}.
    private fun parseResult(result: ReadableMap): PaymentResult {
        fun opt(key: String): String =
            if (result.hasKey(key) && !result.isNull(key)) result.getString(key) ?: "" else ""
        return when (val status = opt("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> failure(
                opt("code").ifEmpty { "UNKNOWN_ERROR" },
                opt("message").ifEmpty { "An error has occurred." },
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
