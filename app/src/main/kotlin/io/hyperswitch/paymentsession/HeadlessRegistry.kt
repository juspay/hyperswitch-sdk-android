package io.hyperswitch.paymentsession

import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap

typealias HeadlessRequestCallback = (PaymentSessionHandler) -> Unit
typealias ConfirmationCallback = (PaymentResult) -> Unit

/** The merchant's pending saved-methods request, waiting for JS to return a handler. */
internal class PendingHeadlessRequest(
    val callback: HeadlessRequestCallback,
    val onTerminalResult: (String, PaymentResult) -> Unit,
    /* Live look-up of the session's current authorization: the delivered handler compares
       it against its launch key at confirm time (see PaymentSessionHandlerImpl). */
    val currentSdkAuthorization: () -> String,
)

/* One correlation table for every headless round trip. Native registers a waiter under
   (kind, sdkAuthorization) before it launches JS; JS calls back into the singleton
   TurboModule and the module takes the waiter by the same key. An in-flight waiter of a kind
   is rejected, a timeout is optional, and a rollback removes only the entry it registered.

   INTERIM(1 session): routing by authorization commented out — a single slot per Kind while
   only one session is supported; tryRegister/take/remove keep their sdkAuthorization
   parameters so callers are untouched. The auth-keyed correlation returns with the
   multisession work (stash "headless-roottag-ios-wip", docs/plans/headless-roottag-ios.md).

   Waiters: PREFETCH holds a CompletableDeferred<ReadableMap>, REQUEST a
   PendingHeadlessRequest, CONFIRM a ConfirmationCallback. Confirms have no timeout on
   purpose: a confirm can wait on a 3DS challenge or a wallet sheet for minutes, and a late
   real result must reach the merchant. A dead runtime is handled by the emit-failure
   rollback in HyperFragment.confirmCvcPayment and the catch branch in
   PaymentSessionHandlerImpl.confirmWithCustomerPaymentToken. */
internal object HeadlessRegistry {

    enum class Kind { PREFETCH, REQUEST, CONFIRM }

    class Entry<T : Any>(val waiter: T) {
        private var timeoutTask: Runnable? = null
        private var done = false

        @Synchronized
        internal fun scheduleTimeout(handler: Handler, delayMillis: Long, onTimeout: () -> Unit) {
            if (done) return
            Runnable(onTimeout).also { task ->
                timeoutTask = task
                handler.postDelayed(task, delayMillis)
            }
        }

        @Synchronized
        internal fun finish(handler: Handler) {
            if (done) return
            done = true
            timeoutTask?.let(handler::removeCallbacks)
            timeoutTask = null
        }
    }

    // INTERIM(1 session): auth-keyed entries commented out; one slot per Kind below.
/*
    private data class Key(val kind: Kind, val sdkAuthorization: String)

    private val entries = ConcurrentHashMap<Key, Entry<*>>()
*/
    private val entries = ConcurrentHashMap<Kind, Entry<*>>()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    /** Registers [waiter]; null when the authorization is empty or a waiter of this kind is already in flight. */
    fun <T : Any> tryRegister(
        kind: Kind,
        sdkAuthorization: String,
        waiter: T,
        timeoutMillis: Long? = null,
        onTimeout: () -> Unit = {},
    ): Entry<T>? {
        if (sdkAuthorization.isEmpty()) return null
        // INTERIM(1 session): val key = Key(kind, sdkAuthorization)
        val entry = Entry(waiter)
        // INTERIM(1 session): if (entries.putIfAbsent(key, entry) != null) return null
        if (entries.putIfAbsent(kind, entry) != null) return null
        if (timeoutMillis != null) {
            entry.scheduleTimeout(timeoutHandler, timeoutMillis) {
                // INTERIM(1 session): if (entries.remove(key, entry)) {
                if (entries.remove(kind, entry)) {
                    entry.finish(timeoutHandler)
                    onTimeout()
                }
            }
        }
        return entry
    }

    /** Consumes the waiter of this kind for the authorization and cancels its timeout. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> take(kind: Kind, sdkAuthorization: String): T? {
        // INTERIM(1 session): val entry = entries.remove(Key(kind, sdkAuthorization)) ?: return null
        val entry = entries.remove(kind) ?: return null
        entry.finish(timeoutHandler)
        return entry.waiter as? T
    }

    /** Rolls back exactly [entry]; a newer registration under the same key is left alone. */
    fun remove(kind: Kind, sdkAuthorization: String, entry: Entry<*>): Boolean {
        // INTERIM(1 session): val removed = entries.remove(Key(kind, sdkAuthorization), entry)
        val removed = entries.remove(kind, entry)
        if (removed) entry.finish(timeoutHandler)
        return removed
    }
}

// `this` is the codegen PaymentExitResult object: {status, type?, code?, message?}.
internal fun ReadableMap.toPaymentResult(): PaymentResult {
    fun opt(key: String): String =
        if (hasKey(key) && !isNull(key)) getString(key) ?: "" else ""

    fun failure(code: String, message: String): PaymentResult.Failed =
        PaymentResult.Failed(Throwable(message).apply { initCause(Throwable(code)) })

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
