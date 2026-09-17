package io.hyperswitch.paymentsession

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.paymentsheet.PaymentResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owner of one saved-payment-methods surface. Holds exactly what that surface's
 * replies need: the merchant completion (fired once), the latest JS confirm
 * callback (JS registers a fresh one after every confirm) and at most one
 * pending result handler.
 */
internal class HeadlessAttempt(
    @Volatile var sdkAuthorization: String,
    private val onHandler: (PaymentSessionHandler) -> Unit,
    /** Builds the map handed to the JS callback; tests substitute a JVM-only map. */
    private val newMap: () -> WritableMap = { Arguments.createMap() },
    private val updating: () -> Boolean = { false },
) {

    /** The latest confirm callback JS registered; taken, not read, by [confirm]. */
    private val jsCallback = AtomicReference<Callback?>(null)
    private val handlerDelivered = AtomicBoolean(false)
    private val pendingResult = AtomicReference<((PaymentResult) -> Unit)?>(null)
    private val refusal = AtomicReference<PaymentResult?>(null)

    internal fun isUpdating(): Boolean = updating()

    internal fun refuseConfirms(result: PaymentResult) {
        jsCallback.set(null)
        refusal.set(result)
    }

    fun onPaymentSession(
        defaultMethod: ReadableMap,
        lastUsedMethod: ReadableMap,
        allMethods: ReadableArray,
        callback: Callback,
    ) {
        jsCallback.set(callback)
        if (handlerDelivered.compareAndSet(false, true)) {
            onHandler(PaymentSessionHandlerImpl(this, defaultMethod, lastUsedMethod, allMethods))
        }
    }

    fun confirm(paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit) {
        refusal.get()?.let { resultHandler(it); return }
        if (updating()) {
            resultHandler(failed("An intent update is in progress; confirm after it completes", "UPDATE_IN_PROGRESS"))
            return
        }
        if (!pendingResult.compareAndSet(null, resultHandler)) {
            resultHandler(failed("Payment confirmation already in progress for this handler", "ALREADY_IN_PROGRESS"))
            return
        }
        // A React callback can be invoked once; JS registers a new one after each confirm.
        val callback = jsCallback.getAndSet(null)
        if (callback == null) {
            pendingResult.set(null)
            resultHandler(failed("The saved payment methods are not ready to confirm", "NOT_INITIALISED"))
            return
        }
        try {
            callback.invoke(newMap().apply {
                putString("paymentToken", paymentToken)
                putString("cvc", cvc)
                putString("sdkAuthorization", sdkAuthorization)
            })
        } catch (_: Exception) {
            pendingResult.set(null)
            resultHandler(failed("The saved payment methods are not ready to confirm", "NOT_INITIALISED"))
        }
    }

    fun onExit(result: PaymentResult) {
        pendingResult.getAndSet(null)?.invoke(result)
    }

    fun cancel() {
        val cancelled = failed("The saved payment methods session was replaced or closed", "CANCELLED")
        refuseConfirms(cancelled)
        onExit(cancelled)
    }

    private fun failed(message: String, code: String) =
        PaymentResult.Failed(Throwable(message).apply { initCause(Throwable(code)) })
}
