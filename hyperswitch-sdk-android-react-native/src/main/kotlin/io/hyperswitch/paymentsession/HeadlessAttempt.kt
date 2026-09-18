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
) {

    /** The latest confirm callback JS registered; taken, not read, by [confirm]. */
    private val jsCallback = AtomicReference<Callback?>(null)
    private val handlerDelivered = AtomicBoolean(false)
    private val pendingResult = AtomicReference<((PaymentResult) -> Unit)?>(null)

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
        if (!pendingResult.compareAndSet(null, resultHandler)) {
            resultHandler(failed("Payment confirmation already in progress for this handler", "ALREADY_IN_PROGRESS"))
            return
        }
        // A React callback can be invoked once; JS registers a new one after each confirm.
        val callback = jsCallback.getAndSet(null)
        if (callback == null) {
            pendingResult.set(null)
            resultHandler(failed("Not Initialised", "Not Initialised"))
            return
        }
        try {
            callback.invoke(newMap().apply {
                putString("paymentToken", paymentToken)
                putString("cvc", cvc)
            })
        } catch (_: Exception) {
            pendingResult.set(null)
            resultHandler(failed("Not Initialised", "Not Initialised"))
        }
    }

    fun onExit(result: PaymentResult) {
        pendingResult.getAndSet(null)?.invoke(result)
    }

    /** The surface is going away: fail whatever is still waiting on it. */
    fun cancel() {
        jsCallback.set(null)
        onExit(failed("The saved payment methods session was replaced or closed", "CANCELLED"))
    }

    private fun failed(message: String, code: String) =
        PaymentResult.Failed(Throwable(message).apply { initCause(Throwable(code)) })
}
