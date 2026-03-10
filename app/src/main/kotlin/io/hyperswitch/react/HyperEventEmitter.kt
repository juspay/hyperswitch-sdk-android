package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.hyperswitch.PaymentEvent
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.PaymentEventSubscription
import java.util.concurrent.ConcurrentLinkedQueue

object HyperEventEmitter {
    private var reactContext: ReactApplicationContext? = null
    private val pendingEvents = ConcurrentLinkedQueue<Pair<String, Map<String, String?>>>()
    private var eventListener: PaymentEventListener? = null
    private var subscriptionEvents: PaymentEventSubscription? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: ReactApplicationContext) {
        reactContext = context
        processPendingEvents()
    }

    fun deinitialize() {
        reactContext = null
        eventListener = null
    }

    /**
     * Set the payment event listener for merchant callbacks
     * @param listener The listener to receive payment events
     * @param subscription The event subscription configuration
     */
    fun setEventListener(listener: PaymentEventListener?, subscription: PaymentEventSubscription? = null) {
        eventListener = listener
        subscriptionEvents = subscription
        android.util.Log.d("HyperEventEmitter", "SetEventListener with ${subscription?.eventTypes?.size ?: 0} subscribed events")
    }

    /**
     * Emit a payment event to merchant listener (if subscribed)
     * @param eventType The type of event (e.g., "payment_method.info.card")
     * @param payload The event payload data
     * @param elementType The element type (default: "payment")
     */
    fun emitPaymentEvent(
        eventType: String,
        payload: Map<String, Any>,
        elementType: String = "payment"
    ) {
        val shouldEmit = isSubscribed(eventType)
        
        android.util.Log.d("HyperEventEmitter", "Emit event: $eventType, shouldEmit: $shouldEmit, payload: $payload")
        
        if (shouldEmit && eventListener != null) {
            val event = PaymentEvent(
                type = eventType,
                elementType = elementType,
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

    fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
        val writableMap = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                "true" -> {
                    writableMap.putBoolean(key, true)
                }
                "false" -> {
                    writableMap.putBoolean(key, false)
                }
                else -> {
                    writableMap.putString(key, value)
                }
            }
        }

        if (reactContext != null && reactContext!!.hasCatalystInstance()) {
            try {
                reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(tag, writableMap)
            } catch (e: Exception) {
                pendingEvents.add(Pair(tag, map))
            }
        } else {
            pendingEvents.add(Pair(tag, map))
        }
    }

    fun confirmCardStatic(map: MutableMap<String, String?>) {
        confirmStatic("confirm", map)
    }

    fun confirmECStatic(map: MutableMap<String, String?>) {
        confirmStatic("confirmEC", map)
    }

    private fun processPendingEvents() {
        if (reactContext?.hasCatalystInstance() != true || reactContext?.catalystInstance?.isDestroyed == true) {
            return
        }
        
        val iterator = pendingEvents.iterator()
        while (iterator.hasNext()) {
            val (tag, map) = iterator.next()
            val writableMap = Arguments.createMap()
            for ((key, value) in map) {
                 when (value) {
                    "true" -> {
                        writableMap.putBoolean(key, true)
                    }
                    "false" -> {
                        writableMap.putBoolean(key, false)
                    }
                    else -> {
                        writableMap.putString(key, value)
                    }
                }
            }
                reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(tag, writableMap)
                iterator.remove()
        }
    }
}