package io.hyperswitch.paymentsession

import io.hyperswitch.payments.paymentlauncher.PaymentResult
import org.json.JSONObject

typealias ExitCallback = (PaymentResult) -> Unit

object ExitHeadlessCallBackManager {
    private var callback: ExitCallback? = null

    fun setCallback(newCallback: (PaymentResult) -> Unit) {
        callback = newCallback
    }

    fun getCallback(): ExitCallback? {
        return callback
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
        callback?.invoke(result) ?: println("No callback set")
    }
}