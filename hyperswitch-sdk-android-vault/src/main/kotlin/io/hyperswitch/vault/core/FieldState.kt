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
    /*
     * Brand detected from the card_number widget, named `cardBrand` for
     * parity with iOS; parsed from the JS `brand` wire key. Absent for
     * non-card fields and while no brand is known.
     */
    val cardBrand: String? = null,
    val isEmpty: Boolean = true,
    val isValid: Boolean = false,
    val isRequired: Boolean = false,
    val isFocused: Boolean = false,
    val isTokenized: Boolean = false,
) {
    companion object {
        fun fromJson(json: String): FieldState {
            val obj = JSONObject(json)
            return FieldState(
                fieldName = obj.optString("fieldName").takeIf { it.isNotEmpty() },
                fieldType = FieldType.fromRawValue(obj.optString("fieldType")) ?: FieldType.INFO,
                bin = obj.optString("bin").takeIf { it.isNotEmpty() },
                cardBrand = obj.optString("brand").takeIf { it.isNotEmpty() },
                isEmpty = obj.optBoolean("isEmpty", true),
                isValid = obj.optBoolean("isValid"),
                isRequired = obj.optBoolean("isRequired"),
                isFocused = obj.optBoolean("isFocused"),
                isTokenized = obj.optBoolean("isTokenized"),
            )
        }
    }
}
