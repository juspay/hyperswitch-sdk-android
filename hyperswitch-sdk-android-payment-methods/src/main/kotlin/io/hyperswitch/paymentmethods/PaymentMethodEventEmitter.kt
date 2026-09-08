package io.hyperswitch.paymentmethods

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Attaches to this host's [PaymentMethodModule] instance and forwards native -> JS events to it.
 *
 * Also holds the pending `CardForm.tokenise()` completions, keyed by the requesting form's
 * surface rootTag — one tokenise may be in flight per form at a time. [PaymentMethodModule]
 * resolves an entry when JS answers via `returnTokenResult`.
 */
internal class PaymentMethodEventEmitter {
    private val moduleRef = AtomicReference<WeakReference<PaymentMethodModule>?>(null)
    private val pendingTokeniseCallbacks = ConcurrentHashMap<Int, (ReadableMap?) -> Unit>()

    fun attach(module: PaymentMethodModule) {
        moduleRef.set(WeakReference(module))
    }

    fun detach() {
        moduleRef.set(null)
    }

    fun emitTokenise(rootTag: Int) {
        val payload = Arguments.createMap().apply { putInt("rootTag", rootTag) }
        moduleRef.get()?.get()?.emitTokeniseEvent(payload)
    }

    fun registerTokeniseCallback(rootTag: Int, callback: (ReadableMap?) -> Unit) {
        pendingTokeniseCallbacks[rootTag] = callback
    }

    fun resolveTokeniseCallback(rootTag: Int, result: ReadableMap?) {
        pendingTokeniseCallbacks.remove(rootTag)?.invoke(result)
    }
}
