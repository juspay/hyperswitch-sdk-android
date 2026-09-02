package io.hyperswitch.paymentsession

import android.os.Handler
import android.os.Looper
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap

typealias HeadlessRequestCallback = (PaymentSessionHandler) -> Unit

internal class PendingHeadlessRequest(
    val callback: HeadlessRequestCallback,
    val onTerminalResult: (String, PaymentResult) -> Unit,
    /* Live look-up of the session's current authorization: the delivered handler compares
       it against its launch key at confirm time (see PaymentSessionHandlerImpl). */
    val currentSdkAuthorization: () -> String,
) {
    private var timeoutTask: Runnable? = null
    private var waiting = true

    @Synchronized
    fun scheduleTimeout(handler: Handler, delayMillis: Long, onTimeout: () -> Unit) {
        if (!waiting) return
        Runnable(onTimeout).also { task ->
            timeoutTask = task
            handler.postDelayed(task, delayMillis)
        }
    }

    @Synchronized
    fun finish(handler: Handler) {
        if (!waiting) return
        waiting = false
        timeoutTask?.let(handler::removeCallbacks)
        timeoutTask = null
    }
}

/* Request shelf of the headless surface: holds pending session-intent GET
   requests still waiting for JS to return their handler. Sibling of
   HeadlessConfirmationRegistry, which holds in-flight payment completions. */
internal object HeadlessRequestRegistry {
    private val requests = ConcurrentHashMap<String, PendingHeadlessRequest>()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    fun tryRegister(
        sdkAuthorization: String,
        request: PendingHeadlessRequest,
        timeoutMillis: Long,
        onTimeout: () -> Unit,
    ): Boolean {
        if (
            sdkAuthorization.isEmpty() ||
            requests.putIfAbsent(sdkAuthorization, request) != null
        ) {
            return false
        }
        request.scheduleTimeout(timeoutHandler, timeoutMillis) {
            if (requests.remove(sdkAuthorization, request)) {
                request.finish(timeoutHandler)
                onTimeout()
            }
        }
        return true
    }

    fun take(sdkAuthorization: String): PendingHeadlessRequest? =
        requests.remove(sdkAuthorization)?.also { it.finish(timeoutHandler) }

    fun remove(
        sdkAuthorization: String,
        request: PendingHeadlessRequest,
    ): Boolean {
        val removed = requests.remove(sdkAuthorization, request)
        if (removed) request.finish(timeoutHandler)
        return removed
    }
}
