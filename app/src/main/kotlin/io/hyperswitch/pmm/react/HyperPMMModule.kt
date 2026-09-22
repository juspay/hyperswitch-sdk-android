package io.hyperswitch.pmm.react

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.codegen.NativeHyperPMMModuleSpec
import io.hyperswitch.react.parsePaymentResult
import org.json.JSONObject

/** The contract between the hyperswitch-payment-method-management bundle and its native owners. */
internal object PMMProtocol {
    const val COMPONENT = "hyperPMM"

    const val SHEET_TYPE = "paymentMethodsManagement"
    const val WIDGET_TYPE = "widgetPaymentMethodsManagement"

    const val CONFIRM_ACTION = "CONFIRM_PAYMENT_ACTION"
    const val TRIGGER_WIDGET_ACTION_EVENT = "triggerWidgetAction"
}

/**
 * The native owner of a PMM root: what the bundle says about that root lands here.
 */
internal interface PaymentMethodManagementEventTarget {
    /** JS says the surface is finished: a sheet dismisses; a widget resolves its pending confirm. */
    fun onPmmExit(resultJson: String, reset: Boolean)

    /** A merchant-driven confirm was refused (e.g. form validation): the surface stays mounted. */
    fun onPmmNonTerminalResult(result: PaymentResult)

    /** Merchant payment-event subscription dispatch. */
    fun onPmmEvent(eventType: String, payload: Map<String, Any?>)
}

internal class HyperPMMModule(
    private val rct: ReactApplicationContext,
    private val runtime: PaymentMethodManagementRuntime,
) : NativeHyperPMMModuleSpec(rct) {

    init {
        runtime.attach(this)
    }

    override fun invalidate() {
        super.invalidate()
        runtime.detach()
    }

    /** Native -> JS. Merchant-driven confirm routed to the widget surface by root tag. */
    fun emitWidgetAction(payload: WritableMap) = emitTriggerWidgetAction(payload)

    override fun exitPaymentMethodManagement(rootTag: Double, result: String, reset: Boolean) {
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            (owner as? PaymentMethodManagementEventTarget)?.onPmmExit(result, reset)
        }
    }

    override fun notifyWidgetPaymentResult(rootTag: Double, result: ReadableMap) {
        val parsed = parsePaymentResult(JSONObject(result.toHashMap()).toString())
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            (owner as? PaymentMethodManagementEventTarget)?.onPmmNonTerminalResult(parsed)
        }
    }

    override fun emitPaymentEvent(rootTag: Double, eventType: String, payload: ReadableMap) {
        val map = payload.toHashMap()
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            (owner as? PaymentMethodManagementEventTarget)?.onPmmEvent(eventType, map)
        }
    }
}

/** Binds the PMM module to the runtime that owns its host. */
internal class PaymentMethodManagementPackage(
    private val runtime: PaymentMethodManagementRuntime,
) : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == NativeHyperPMMModuleSpec.NAME) HyperPMMModule(reactContext, runtime) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
        mapOf(
            NativeHyperPMMModuleSpec.NAME to ReactModuleInfo(
                NativeHyperPMMModuleSpec.NAME,
                NativeHyperPMMModuleSpec.NAME,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = true,
            ),
        )
    }
}
