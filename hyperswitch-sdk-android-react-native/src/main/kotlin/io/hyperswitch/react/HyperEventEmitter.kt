package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.PaymentEvent
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.PaymentEventSubscription
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class HyperEventEmitter {
    private val moduleRef = AtomicReference<WeakReference<HyperModule>?>(null)
    @Volatile private var eventListener: PaymentEventListener? = null
    @Volatile private var subscriptionEvents: PaymentEventSubscription? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(module: HyperModule) {
        moduleRef.set(WeakReference(module))
    }

    fun detach() {
        moduleRef.set(null)
        eventListener = null
        subscriptionEvents = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Set the payment event listener for merchant callbacks
     * @param listener The listener to receive payment events
     * @param subscription The event subscription configuration
     */
    fun setEventListener(listener: PaymentEventListener?, subscription: PaymentEventSubscription? = null) {
        eventListener = listener
        subscriptionEvents = subscription
    }

    /**
     * Emit a payment event to merchant listener (if subscribed)
     * @param eventType The type of event (e.g., "payment_method.info.card")
     * @param payload The event payload data
     */
    fun emitPaymentEvent(
        eventType: String,
        payload: Map<String, Any>
    ) {
        val shouldEmit = isSubscribed(eventType)

        if (shouldEmit && eventListener != null) {
            val event = PaymentEvent(
                type = eventType,
                payload = payload
            )

            mainHandler.post {
                eventListener?.onPaymentEvent(event)
            }
        }
    }

    fun isSubscribed(eventType: String): Boolean {
        val subscription = subscriptionEvents ?: return false
        return subscription.isSubscribed(eventType)
    }

    /**
     * Get the list of subscribed event types
     * Called by React Native to initialize subscription state
     * @return List of subscribed event type strings
     */
    fun getSubscribedEvents(): List<String> {
        val subscription = subscriptionEvents ?: return emptyList()
        return subscription.getSubscribedEventStrings()
    }

    fun emitEvent(tag: String, payload: WritableMap): Boolean {
        val module = moduleRef.get()?.get() ?: return false
        return try {
            module.emitEvent(tag, payload)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun confirm(tag: String, map: MutableMap<String, String?>) {
        emitEvent(tag, toWritableMap(map))
    }

    fun confirmCard(map: MutableMap<String, String?>) {
        confirm("confirm", map)
    }

    fun confirmEC(map: MutableMap<String, String?>) {
        confirm("confirmEC", map)
    }

    private fun toWritableMap(map: Map<String, String?>): WritableMap {
        val writableMap = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                "true" -> writableMap.putBoolean(key, true)
                "false" -> writableMap.putBoolean(key, false)
                else -> writableMap.putString(key, value)
            }
        }
        return writableMap
    }

}
