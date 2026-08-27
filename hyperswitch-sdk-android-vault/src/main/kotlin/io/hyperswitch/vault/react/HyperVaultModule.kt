package io.hyperswitch.vault.react

import com.facebook.react.bridge.ReactApplicationContext
import io.hyperswitch.vault.core.FieldState
import io.hyperswitch.react.codegen.NativeHyperVaultModuleSpec

/**
 * HyperVaultModule
 *
 * TurboModule called by every JS-rendered vault field (src/vault) to push its
 * state ({fieldName, fieldType, value, isEmpty, isValid, isRequired, isFocused,
 * isTokenized}) into the native SDK, keyed by the surface's rootTag.
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
}
