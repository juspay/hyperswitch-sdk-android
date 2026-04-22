package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentResult

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

typealias ExitCallback = (PaymentResult) -> Unit

object ExitHeadlessCallBackManager {
    private val callbackRef = AtomicReference<ExitCallback?>(null)

    fun setCallback(newCallback: (PaymentResult) -> Unit) {
        callbackRef.set(newCallback)
    }

    fun getCallback(): ExitCallback? {
        return callbackRef.get()
    }

    fun executeCallback(data: String) {
        val message = JSONObject(data)
        val result = when (val status = message.getString("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val throwable = Throwable(message.getString("message"))
                throwable.initCause(Throwable(message.getString("code")))
                PaymentResult.Failed(throwable)
            }

            else -> PaymentResult.Completed(status ?: "default")
        }
        val cb = callbackRef.getAndSet(null)
        cb?.invoke(result)

    }
}
