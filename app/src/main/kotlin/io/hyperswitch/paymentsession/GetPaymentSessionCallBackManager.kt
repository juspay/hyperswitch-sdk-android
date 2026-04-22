package io.hyperswitch.paymentsession

import java.util.concurrent.atomic.AtomicReference

typealias SessionCallback = (PaymentSessionHandler) -> Unit

object GetPaymentSessionCallBackManager {
    private val callbackRef = AtomicReference<SessionCallback?>(null)
    private val sdkAuthorizationRef = AtomicReference<String?>(null)

    fun setCallback(sdkAuthorization: String?, newCallback: SessionCallback?) {
        callbackRef.set(newCallback)
        sdkAuthorizationRef.set(sdkAuthorization)
    }

    fun getCallback(): SessionCallback? = callbackRef.get()

    fun getSdkAuthorization(): String = sdkAuthorizationRef.get() ?: ""

    fun executeCallback(data: PaymentSessionHandler) {
        callbackRef.getAndSet(null)?.invoke(data)
            ?: println("No callback set")
    }
}
