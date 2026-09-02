package io.hyperswitch.paymentsession

import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap

typealias ConfirmationCallback = (PaymentResult) -> Unit

private class ConfirmationEntry(
    val callback: ConfirmationCallback,
) {
    private var timeoutTask: Runnable? = null
    private var done = false

    @Synchronized
    fun scheduleTimeout(handler: Handler, delayMillis: Long, onTimeout: () -> Unit) {
        if (done) return
        Runnable(onTimeout).also { task ->
            timeoutTask = task
            handler.postDelayed(task, delayMillis)
        }
    }

    @Synchronized
    fun finish(handler: Handler) {
        if (done) return
        done = true
        timeoutTask?.let(handler::removeCallbacks)
        timeoutTask = null
    }
}

/* Holds only confirmations currently running through the headless runtime. Registrations
   are time-boxed like the prefetch and saved-methods requests: a wedged runtime must not
   lock the authorization's slot forever — the entry settles as a failure after 30s. */
internal object HeadlessConfirmationRegistry {
    private const val CONFIRMATION_TIMEOUT_MS = 30_000L

    private val callbacks = ConcurrentHashMap<String, ConfirmationEntry>()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    fun tryRegister(
        sdkAuthorization: String,
        callback: ConfirmationCallback,
    ): Boolean {
        val entry = ConfirmationEntry(callback)
        if (
            sdkAuthorization.isEmpty() ||
            callbacks.putIfAbsent(sdkAuthorization, entry) != null
        ) {
            return false
        }
        entry.scheduleTimeout(timeoutHandler, CONFIRMATION_TIMEOUT_MS) {
            if (callbacks.remove(sdkAuthorization) != null) {
                callback(
                    PaymentResult.Failed(
                        Throwable("Confirmation did not complete in time").apply {
                            initCause(Throwable("CONFIRM_RESULT_TIMEOUT"))
                        }
                    )
                )
            }
        }
        return true
    }

    fun complete(sdkAuthorization: String, result: ReadableMap): Boolean {
        val entry = callbacks.remove(sdkAuthorization) ?: return false
        entry.finish(timeoutHandler)
        entry.callback(parseResult(result))
        return true
    }

    fun remove(sdkAuthorization: String) {
        callbacks.remove(sdkAuthorization)?.finish(timeoutHandler)
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
