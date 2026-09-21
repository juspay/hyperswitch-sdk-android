package io.hyperswitch.paymentmethods.react

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.codegen.NativeHyperPaymentMethodsSpec

/**
 * The contract with the Payment Methods bundle. Mirrors `hosted/protocol.ts` in
 * hyperswitch-client-core; the two are told apart from a mismatch by [VERSION].
 */
internal object PaymentMethodsProtocol {
    const val VERSION = 1

    const val FORM_COMPONENT = "HyperPaymentMethodsForm"
    const val FIELD_COMPONENT = "HyperPaymentMethodsField"

    const val FORM_TYPE = "paymentMethodsForm"
    const val FIELD_TYPE = "paymentMethodsField"

    const val FIELD_READY = "PM_FIELD_READY"
    const val FIELD_CHANGE = "PM_FIELD_CHANGE"
    const val FIELD_FOCUS = "PM_FIELD_FOCUS"
    const val FIELD_BLUR = "PM_FIELD_BLUR"
    const val FIELD_LAYOUT = "PM_LAYOUT"
    const val FIELD_ERROR = "PM_FIELD_ERROR"

    const val FORM_READY = "PM_FORM_READY"
    const val FORM_CHANGE = "PM_FORM_CHANGE"
    const val FORM_ERROR = "PM_FORM_ERROR"
    const val COMMAND_RESULT = "PM_COMMAND_RESULT"
}

/** The native owner of a Payment Methods root: what the bundle says about that root lands here. */
internal fun interface PaymentMethodsEventTarget {
    fun onPaymentMethodsEvent(name: String, payload: Map<String, Any?>)
}

internal class PaymentMethodsModule(
    private val rct: ReactApplicationContext,
    private val runtime: PaymentMethodsRuntime,
) : NativeHyperPaymentMethodsSpec(rct) {

    init {
        runtime.attach(this)
    }

    override fun invalidate() {
        super.invalidate()
        runtime.detach()
    }

    fun sendCommand(command: ReadableMap) = emitOnCommand(command)

    override fun emitFieldEvent(rootTag: Double, eventName: String, payload: ReadableMap) =
        deliver(rootTag, eventName, payload)

    override fun emitFormEvent(rootTag: Double, eventName: String, payload: ReadableMap) =
        deliver(rootTag, eventName, payload)

    /**
     * A root tag names its root view and the view carries its owner, so an event finds its
     * form or field without anything here keeping track of them.
     */
    private fun deliver(rootTag: Double, eventName: String, payload: ReadableMap) {
        val map = payload.toHashMap()
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            (owner as? PaymentMethodsEventTarget)?.onPaymentMethodsEvent(eventName, map)
        }
    }
}

/** Binds the Payment Methods module to the runtime that owns its host. */
internal class PaymentMethodsPackage(
    private val runtime: PaymentMethodsRuntime,
) : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == NativeHyperPaymentMethodsSpec.NAME) PaymentMethodsModule(reactContext, runtime) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
        mapOf(
            NativeHyperPaymentMethodsSpec.NAME to ReactModuleInfo(
                NativeHyperPaymentMethodsSpec.NAME,
                NativeHyperPaymentMethodsSpec.NAME,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = true,
            ),
        )
    }
}
