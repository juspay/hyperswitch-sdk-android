package io.hyperswitch.paymentmethods

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.hyperswitch.react.codegen.NativePaymentMethodsEventEmitterSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * New-arch typed event emitter for payment-method sessions.
 *
 * Emits events to the JS side of this session's React host via the app-level
 * [DeviceEventManagerModule.RCTDeviceEventEmitter]; JS subscribes with
 * `NativeEventEmitter`. Currently carries the `tokenise` event that triggers
 * `cardForm.tokenize()` on the JS-side `PaymentMethodsTask` surface, whose
 * result is written back through [returnTokeniseResult] and routed to the
 * callback registered by the originating `CardForm.tokenise` call — one
 * pending callback per form surface rootTag.
 */
class PaymentMethodsEventEmitterModule(
    reactContext: ReactApplicationContext,
) : NativePaymentMethodsEventEmitterSpec(reactContext) {

    /** Pending tokenise completions, keyed by the form surface rootTag. */
    private val pendingTokeniseCallbacks = ConcurrentHashMap<Int, (ReadableMap?) -> Unit>()

    /** Emits [payload] for the event [name] to all JS listeners of this module. */
    fun sendEvent(name: String, payload: WritableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(name, payload)
    }

    /** Holds [callback] until the JS side answers with [returnTokeniseResult]. */
    fun registerTokeniseCallback(rootTag: Int, callback: (ReadableMap?) -> Unit) {
        pendingTokeniseCallbacks[rootTag] = callback
    }

    /** JS -> native delivery of the `tokenize()` result, routed by rootTag. */
    override fun returnTokeniseResult(rootTag: Double, result: ReadableMap) {
        pendingTokeniseCallbacks.remove(rootTag.toInt())?.invoke(result)
    }

    override fun addListener(eventName: String) {
        // JS NativeEventEmitter bookkeeping; nothing to start on the native side.
    }

    override fun removeListeners(count: Double) {
        // JS NativeEventEmitter bookkeeping; nothing to stop on the native side.
    }

    companion object {
        /** Runtime module name — must match the codegen spec name. */
        const val NAME = "PaymentMethodsEventEmitter"

        /** Emitted when native requests tokenization of the session's card form. */
        const val EVENT_TOKENISE = "tokenise"
    }
}
