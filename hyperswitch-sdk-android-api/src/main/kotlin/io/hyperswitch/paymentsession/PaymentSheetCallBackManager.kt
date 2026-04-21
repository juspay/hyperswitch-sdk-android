package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentResult
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

typealias Callback = (PaymentResult) -> Unit

object PaymentSheetCallbackManager {
    private val callback = AtomicReference<Callback?>(null)
    private var isFragment: Boolean = true

    fun setCallback(newCallback: Callback, newIsFragment: Boolean = true) {
        callback.set(newCallback)
        isFragment = newIsFragment
    }

    fun getCallback(): Callback? {
        return callback.get()
    }

    fun executeCallback(data: String): Boolean {
        val jsonObject = JSONObject(data)
        val result = when (val status = jsonObject.getString("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val message = jsonObject.getString("message")
                val throwable = Throwable(message.ifEmpty { status })
                throwable.initCause(Throwable(jsonObject.getString("code")))
                PaymentResult.Failed(throwable)
            }

            else -> PaymentResult.Completed(status)
        }

        // Atomically get and clear callback to prevent double-invocation
        val currentCallback = callback.getAndSet(null)
        currentCallback?.invoke(result) ?: println("No callback set")
        return isFragment
    }
}