package io.hyperswitch.vault.react

import com.facebook.react.bridge.ReactApplicationContext
import io.hyperswitch.vault.core.FieldState
import io.hyperswitch.react.codegen.NativeHyperVaultModuleSpec
import org.json.JSONArray

/**
 * HyperVaultModule
 *
 * TurboModule called by the JS vault package:
 * - updateFieldState: the legacy per-surface state push, keyed by rootTag.
 * - updateVaultFieldStates: the aggregated push of a surface's mounted
 *   fields ([{fieldType, value(redacted), isEmpty, isValid, ...}]), keyed by
 *   FieldType. Values are redacted JS-side (PAN to BIN, CVC masked).
 * - submitTokeniseResult: the JS answer to a native tokenise() broadcast;
 *   resolves the pending collector completion via TokeniseDispatcher.
 */
class HyperVaultModule(reactContext: ReactApplicationContext) :
    NativeHyperVaultModuleSpec(reactContext) {

    companion object {
        const val NAME = "HyperVaultModule"
    }

    override fun getName(): String = NAME

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

    override fun submitTokeniseResult(requestId: Double, resultJson: String) {
        TokeniseDispatcher.resolve(requestId.toLong(), resultJson)
    }
}
