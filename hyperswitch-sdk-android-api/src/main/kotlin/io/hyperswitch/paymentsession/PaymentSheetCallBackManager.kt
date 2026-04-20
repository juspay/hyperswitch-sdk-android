package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentResult
import org.json.JSONObject

typealias Callback = (PaymentResult) -> Unit

object PaymentSheetCallbackManager {
    private var callback: Callback? = null
    private var isFragment: Boolean = true

    fun setCallback(newCallback: Callback, newIsFragment: Boolean = true) {
        callback = newCallback
        isFragment = newIsFragment
    }

    fun getCallback(): Callback? {
        return callback
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
        callback?.invoke(result) ?: println("No callback set")
        return isFragment
    }
}