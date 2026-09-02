package io.hyperswitch.vault.react

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.vault.core.FieldState
import io.hyperswitch.vault.core.VaultTokeniseRequest
import io.hyperswitch.react.codegen.NativeHyperVaultModuleSpec
import android.util.Log
import org.json.JSONArray

/**
 * HyperVaultModule
 *
 * TurboModule called by the JS vault package:
 * - updateFieldState: the legacy per-surface state push, keyed by rootTag.
 * - updateVaultFieldStates: aggregated push of a surface's mounted fields
 *   ([{fieldType, bin, isEmpty, isValid, ...}]), keyed by FieldType. No raw
 *   values: card_number carries only the PCI-safe BIN under `bin`; expiry
 *   and CVC send flags only.
 * - returnTokenizedValue: the JS answer to a native tokenise() broadcast;
 *   resolves the pending collector completion via TokeniseDispatcher.
 */
class HyperVaultModule(reactContext: ReactApplicationContext) :
    NativeHyperVaultModuleSpec(reactContext) {

    companion object {
        const val NAME = "HyperVaultModule"
    }

    override fun getName(): String = NAME

    /* The module makes itself reachable for native → JS typed broadcasts the
     * moment the runtime spins it up; twin of the main SDK's HyperModule ↔
     * HyperEventEmitter wiring (the triggerWidgetAction channel). */
    override fun initialize() {
        super.initialize()
        VaultEventEmitter.attach(this)
    }

    override fun invalidate() {
        super.invalidate()
        VaultEventEmitter.detach()
    }

    override fun updateFieldState(rootTag: Double, state: String) {
        val parsed = kotlin.runCatching { FieldState.fromJson(state) }.getOrNull()
        if (parsed != null) {
            VaultStateStore.put(rootTag.toInt(), parsed)
        }
    }

    override fun updateVaultFieldStates(statesJson: String) {
        val array = kotlin.runCatching { JSONArray(statesJson) }.getOrNull() ?: return
        for (i in 0 until array.length()) {
            val parsed =
                kotlin.runCatching { FieldState.fromJson(array.getString(i)) }.getOrNull()
                    ?: continue
            VaultStateStore.putByType(parsed.fieldType.rawValue, parsed)
        }
    }

    override fun returnTokenizedValue(resultJson: String) {
        TokeniseDispatcher.resolve(resultJson)
    }

    /* Native → JS broadcast surface for VaultEventEmitter: the codegen-
     * generated emitOnVaultTokenise is protected, so typed emission funnels
     * through this method (twin of the main SDK's HyperModule.emitEvent). */
    fun emitEvent(name: String, payload: WritableMap) {
        when (name) {
            VaultTokeniseRequest.EVENT_NAME -> emitOnVaultTokenise(payload)
            else -> Log.w(NAME, "emitEvent: unknown event name $name")
        }
    }

    /** Broadcasts the typed tokenise request to the JS vault surfaces. */
    fun emitVaultTokenise(request: VaultTokeniseRequest) {
        emitOnVaultTokenise(request.toWritableMap())
    }
}
