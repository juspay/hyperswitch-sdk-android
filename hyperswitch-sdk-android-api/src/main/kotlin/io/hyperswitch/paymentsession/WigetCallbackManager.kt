package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentSheetResult
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap


data class OnEventResult(
    val eventName: String,
    val payload: String? = null
)

typealias EventCallback = (OnEventResult) -> Unit

object WidgetCallbackManager {
    private val paymentCallbacks = ConcurrentHashMap<String, Callback>()
    private val eventCallbacks = ConcurrentHashMap<String, EventCallback>()
    private val fragmentFlags = ConcurrentHashMap<String, Boolean>()
    fun setCallback(
        callback: Callback,
        isFragment: Boolean = true,
        sessionId: String = ""
    ) {
        paymentCallbacks[sessionId] = callback
        fragmentFlags[sessionId] = isFragment
    }

    fun getCallback(sessionId: String): Callback? {
        return paymentCallbacks.get(sessionId)
    }

    fun executeCallback(data: String, sessionId: String = ""): Boolean {
        val callback = getCallback(sessionId) ?: return false
        val jsonObject = JSONObject(data)
        val result = when (val status = jsonObject.getString("status")) {
            "cancelled" -> PaymentSheetResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val message = jsonObject.getString("message")
                val throwable = Throwable(message.ifEmpty { status })
                throwable.initCause(Throwable(jsonObject.getString("code")))
                PaymentSheetResult.Failed(throwable)
            }

            else -> PaymentSheetResult.Completed(status)
        }
        callback.invoke(result) ?: println("No callback set")
        removeSession(sessionId)
        return fragmentFlags[sessionId] ?: true
    }

    fun setEventCallback(sessionId: String, callback: EventCallback) {
        eventCallbacks[sessionId] = callback
    }

    fun sendEvent(sessionId: String, eventName: String, payload: String? = null) {
        eventCallbacks[sessionId]?.invoke(
            OnEventResult(eventName, payload)
        )
    }

    fun removeSession(sessionId: String) {
        paymentCallbacks.remove(sessionId)
        eventCallbacks.remove(sessionId)
        fragmentFlags.remove(sessionId)
    }

}