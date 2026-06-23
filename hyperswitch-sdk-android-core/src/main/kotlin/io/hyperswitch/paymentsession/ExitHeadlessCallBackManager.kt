package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentResult

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

typealias ExitCallback = (PaymentResult) -> Unit

object ExitHeadlessCallBackManager {
    private val callbacks = ConcurrentHashMap<Int, ExitCallback>()

    fun tryRegisterCallback(rootTag: Int, callback: ExitCallback): Boolean {
        return if (rootTag == -1) {
            callbacks[-1] = callback
            true
        } else {
            callbacks.putIfAbsent(rootTag, callback) == null
        }
    }

    fun executeCallback(rootTag: Int, data: String): Boolean {
        val cb = callbacks.remove(rootTag) ?: callbacks.remove(-1)

        val result = parseResult(data)
        cb?.invoke(result)
        return true
    }

    fun clearCallback(rootTag: Int) {
        callbacks.remove(rootTag)
    }

    private fun parseResult(data: String): PaymentResult {
        val message = JSONObject(data)
        return when (val status = message.getString("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val throwable = Throwable(message.getString("message"))
                throwable.initCause(Throwable(message.getString("code")))
                PaymentResult.Failed(throwable)
            }

            else -> PaymentResult.Completed(status ?: "default")
        }
    }
}
