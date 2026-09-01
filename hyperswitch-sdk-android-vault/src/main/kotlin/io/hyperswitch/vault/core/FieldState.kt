package io.hyperswitch.vault.core

import org.json.JSONObject

/**
 * Redacted snapshot pushed from every JS-rendered vault field. The raw value
 * never crosses the bridge — only flags and (for `card_number`) the PCI-safe
 * 6-digit BIN required for brand lookup. There is no `value`/`text` member.
 */
data class FieldState(
    val fieldName: String?,
    val fieldType: FieldType,
    val bin: String?,
    val isEmpty: Boolean,
    val isValid: Boolean,
    val isRequired: Boolean,
    val isFocused: Boolean,
    val isTokenized: Boolean = false,
) {
    companion object {
        fun fromJson(json: String): FieldState {
            val obj = JSONObject(json)
            return FieldState(
                fieldName = obj.optString("fieldName").takeIf { it.isNotEmpty() },
                fieldType = FieldType.fromRawValue(obj.optString("fieldType")) ?: FieldType.INFO,
                bin = obj.optString("bin").takeIf { it.isNotEmpty() },
                isEmpty = obj.optBoolean("isEmpty", true),
                isValid = obj.optBoolean("isValid"),
                isRequired = obj.optBoolean("isRequired"),
                isFocused = obj.optBoolean("isFocused"),
                isTokenized = obj.optBoolean("isTokenized"),
            )
        }
    }
}
