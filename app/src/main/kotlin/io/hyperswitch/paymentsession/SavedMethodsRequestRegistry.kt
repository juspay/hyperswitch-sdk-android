package io.hyperswitch.paymentsession

import android.os.Handler
import android.os.Looper
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.ConcurrentHashMap

typealias SavedMethodsCallback = (PaymentSessionHandler) -> Unit

internal class PendingSavedMethodsRequest(
    val callback: SavedMethodsCallback,
    val onTerminalResult: (String, PaymentResult) -> Unit,
    val currentSdkAuthorization: () -> String,
) {
    private var timeoutTask: Runnable? = null
    private var waiting = true

    fun isCurrent(sdkAuthorization: String): Boolean =
        currentSdkAuthorization() == sdkAuthorization

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

/** Holds only saved-method requests that are still waiting for JS to return their handler. */
internal object SavedMethodsRequestRegistry {
    private val requests = ConcurrentHashMap<String, PendingSavedMethodsRequest>()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    fun tryRegister(
        sdkAuthorization: String,
        request: PendingSavedMethodsRequest,
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

    fun take(sdkAuthorization: String): PendingSavedMethodsRequest? =
        requests.remove(sdkAuthorization)?.also { it.finish(timeoutHandler) }

    fun remove(
        sdkAuthorization: String,
        request: PendingSavedMethodsRequest,
    ): Boolean {
        val removed = requests.remove(sdkAuthorization, request)
        if (removed) request.finish(timeoutHandler)
        return removed
    }
}
