package io.hyperswitch.paymentsession

typealias SessionCallback = (PaymentSessionHandler) -> Unit

object GetPaymentSessionCallBackManager {
    private var callback: SessionCallback? = null

    fun setCallback(newCallback: (PaymentSessionHandler) -> Unit) {
        callback = newCallback
    }

    fun getCallback(): SessionCallback? {
        return callback
    }

    fun executeCallback(data: PaymentSessionHandler) {
        callback?.invoke(data) ?: println("No callback set")
    }
}