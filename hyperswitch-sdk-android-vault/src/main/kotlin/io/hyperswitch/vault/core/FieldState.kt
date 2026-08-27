package io.hyperswitch.vault.core

import org.json.JSONObject

data class FieldState(
    val fieldName: String?,
    val fieldType: FieldType,
    val content: String,
    val isEmpty: Boolean,
    val isValid: Boolean,
    val isRequired: Boolean,
    val isFocused: Boolean,
    val isTokenized: Boolean = false,
) {
    companion object {
        fun fromJson(json: String): FieldState {
            val obj = JSONObject(json)
            val content = obj.optString("value", "")
            return FieldState(
                fieldName = obj.optString("fieldName").takeIf { it.isNotEmpty() },
                fieldType = FieldType.fromRawValue(obj.optString("fieldType")) ?: FieldType.INFO,
                content = content,
                isEmpty = obj.optBoolean("isEmpty", content.isEmpty()),
                isValid = obj.optBoolean("isValid", content.isNotEmpty()),
                isRequired = obj.optBoolean("isRequired"),
                isFocused = obj.optBoolean("isFocused"),
                isTokenized = obj.optBoolean("isTokenized"),
            )
        }
    }
}
