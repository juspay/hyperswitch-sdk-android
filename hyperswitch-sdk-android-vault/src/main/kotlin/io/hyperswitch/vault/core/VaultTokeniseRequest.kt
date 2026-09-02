package io.hyperswitch.vault.core

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/**
 * Typed native → JS tokenise broadcast payload (request channel).
 *
 * Single Kotlin source of truth for the `hsVaultTokenise` event contract;
 * the JS decoder lives in
 * hyperswitch-client-core/src/vault/VaultTokenise.res
 * (types in src/specs/NativeHyperVaultModule.ts),
 * the Swift peer in VaultTokeniseRequest.swift — keep all three in sync.
 */
data class VaultTokeniseRequest(
    /** Base64 JSON carrying payment_method_session_id; null = the claiming
     *  surface falls back to its own sdkAuthorization. */
    val sdkAuthorization: String? = null,
    /** "sandbox" | "integration" | "production"; null = surface fallback. */
    val environment: String? = null,
) {
    /** Wire shape: a JSON object; absent members are simply not encoded. */
    fun toWritableMap(): WritableMap =
        Arguments.createMap().apply {
            sdkAuthorization?.let { putString("sdkAuthorization", it) }
            environment?.let { putString("environment", it) }
        }

    companion object {
        /** The codegen EventEmitter key this payload travels on — MUST match
         *  the `onVaultTokenise` property in src/specs/NativeHyperVaultModule.ts. */
        const val EVENT_NAME = "onVaultTokenise"
    }
}
