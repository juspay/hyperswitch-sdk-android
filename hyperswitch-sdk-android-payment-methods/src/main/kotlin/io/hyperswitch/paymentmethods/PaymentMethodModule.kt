package io.hyperswitch.paymentmethods

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap

/**
 * Dedicated TurboModule for the payment-methods session's JS runtime — kept separate from the
 * main SDK's shared `HyperModule` so that payment-method events never depend on the main SDK's
 * event bus. Registered only on a [PaymentMethodSession]'s own host, via [PaymentMethodPackage].
 */
class PaymentMethodModule internal constructor(
    reactContext: ReactApplicationContext,
    private val emitter: PaymentMethodEventEmitter,
) : io.hyperswitch.react.codegen.NativePaymentMethodModuleSpec(reactContext) {

    override fun initialize() {
        super.initialize()
        emitter.attach(this)
    }

    override fun invalidate() {
        super.invalidate()
        emitter.detach()
    }

    internal fun emitTokeniseEvent(payload: WritableMap) {
        emitTokenise(payload)
    }

    override fun returnTokenResult(rootTag: Double, result: ReadableMap) {
        emitter.resolveTokeniseCallback(rootTag.toInt(), result)
    }

    internal companion object {
        const val NAME = "PaymentMethodModule"
    }
}
