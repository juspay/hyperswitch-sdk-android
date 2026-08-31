package io.hyperswitch.vault.core

import org.json.JSONObject

/**
 * Outcome of HyperswitchCollect.tokenise(completion).
 *
 * Mirrors the JS vault package's `vaultSubmitResult` serialization
 * ({status, token?, error?}) one-to-one, so a natively-driven tokenise and a
 * merchant-RN submit() report identically.
 */
sealed class VaultTokeniseResult {

    data class Success(val token: String) : VaultTokeniseResult()

    data class ValidationError(val code: String, val message: String) : VaultTokeniseResult()

    data class NotReady(val code: String, val message: String) : VaultTokeniseResult()

    data class Error(val code: String, val message: String) : VaultTokeniseResult()

    companion object {
        fun fromJson(json: String): VaultTokeniseResult {
            val obj = JSONObject(json)
            val error = obj.optJSONObject("error")
            val code = error?.optString("code").orEmpty()
            val message = error?.optString("message").orEmpty()
            return when (obj.optString("status")) {
                "success" -> Success(obj.optString("token"))
                "validation_error" -> ValidationError(code, message)
                "not_ready" -> NotReady(code, message)
                else -> Error(code, message)
            }
        }
    }
}
