package io.hyperswitch.vault.core

import android.os.Bundle
import androidx.annotation.ColorInt

/** Per-scheme color overrides, hyperswitch appearance configs. */
data class VaultColors(
    @ColorInt val background: Int? = null,
    @ColorInt val border: Int? = null,
    @ColorInt val borderFocused: Int? = null,
    @ColorInt val text: Int? = null,
    @ColorInt val hint: Int? = null,
    @ColorInt val error: Int? = null,
)

data class VaultAppearanceColors(
    val light: VaultColors? = null,
    val dark: VaultColors? = null,
)

data class VaultShadow(
    @ColorInt val color: Int? = null,
    val opacity: Float? = null,
    val radius: Float? = null,
    val offsetX: Float? = null,
    val offsetY: Float? = null,
    val elevation: Float? = null,
)

/**
 * UI appearance of a vault field. Forwarded straight into the `hs-vault`
 * component's `config.appearance` prop.
 */
data class VaultAppearance(
    val colors: VaultAppearanceColors? = null,
    val radius: Float? = null,
    val borderWidth: Float? = null,
    val padding: Float? = null,
    val fontSize: Float? = null,
    val shadow: VaultShadow? = null,
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        colors?.let { c ->
            putBundle("colors", Bundle().apply {
                c.light?.let { putBundle("light", it.toBundle()) }
                c.dark?.let { putBundle("dark", it.toBundle()) }
            })
        }
        radius?.let { putDouble("radius", it.toDouble()) }
        borderWidth?.let { putDouble("borderWidth", it.toDouble()) }
        padding?.let { putDouble("padding", it.toDouble()) }
        fontSize?.let { putDouble("fontSize", it.toDouble()) }
        shadow?.let { s ->
            putBundle("shadow", Bundle().apply {
                s.color?.let { putString("color", it.toHex()) }
                s.opacity?.let { putDouble("opacity", it.toDouble()) }
                s.radius?.let { putDouble("radius", it.toDouble()) }
                s.offsetX?.let { putDouble("offsetX", it.toDouble()) }
                s.offsetY?.let { putDouble("offsetY", it.toDouble()) }
                s.elevation?.let { putDouble("elevation", it.toDouble()) }
            })
        }
    }

    internal companion object {
        fun VaultColors.toBundle(): Bundle = Bundle().apply {
            background?.let { putString("background", it.toHex()) }
            border?.let { putString("border", it.toHex()) }
            borderFocused?.let { putString("borderFocused", it.toHex()) }
            text?.let { putString("text", it.toHex()) }
            hint?.let { putString("hint", it.toHex()) }
            error?.let { putString("error", it.toHex()) }
        }

        fun Int.toHex(): String = String.format("#%08X", 0xFFFFFFFF and this.toLong())
    }
}
