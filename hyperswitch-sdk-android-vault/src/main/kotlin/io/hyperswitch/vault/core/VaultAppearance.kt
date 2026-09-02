package io.hyperswitch.vault.core

import android.os.Bundle
import androidx.annotation.ColorInt

/**
 * UI appearance of one vault field — the merchant-owned theme token bag that
 * travels inside `configuration.appearance` of the React Native surface's
 * initial props. Flat key set matching the React Native vault's `appearance`
 * prop contract exactly (no translation layer on the JS side).
 */
data class VaultAppearance(
    @param:ColorInt val primaryColor: Int? = null,
    @param:ColorInt val textColor: Int? = null,
    @param:ColorInt val errorColor: Int? = null,
    @param:ColorInt val placeholderColor: Int? = null,
    @param:ColorInt val backgroundColor: Int? = null,
    @param:ColorInt val borderColor: Int? = null,
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    val fontFamily: String? = null,
    val inputHeight: Float? = null,
    val fontScale: Float? = null,
    val gap: Float? = null,
    val placeholderTextSizeAdjust: Float? = null,
    val errorTextSizeAdjust: Float? = null,
    val errorMessageSpacing: Float? = null,
    val brandIconMode: BrandIconMode? = null,
) {
    enum class BrandIconMode(val raw: String) {
        AUTO("auto"),
        STATIC("static"),
        HIDDEN("hidden"),
    }

    internal fun toBundle(): Bundle = Bundle().apply {
        primaryColor?.let { putString("primaryColor", it.toHex()) }
        textColor?.let { putString("textColor", it.toHex()) }
        errorColor?.let { putString("errorColor", it.toHex()) }
        placeholderColor?.let { putString("placeholderColor", it.toHex()) }
        backgroundColor?.let { putString("backgroundColor", it.toHex()) }
        borderColor?.let { putString("borderColor", it.toHex()) }
        borderRadius?.let { putDouble("borderRadius", it.toDouble()) }
        borderWidth?.let { putDouble("borderWidth", it.toDouble()) }
        fontFamily?.let { putString("fontFamily", it) }
        inputHeight?.let { putDouble("inputHeight", it.toDouble()) }
        fontScale?.let { putDouble("fontScale", it.toDouble()) }
        gap?.let { putDouble("gap", it.toDouble()) }
        placeholderTextSizeAdjust?.let { putDouble("placeholderTextSizeAdjust", it.toDouble()) }
        errorTextSizeAdjust?.let { putDouble("errorTextSizeAdjust", it.toDouble()) }
        errorMessageSpacing?.let { putDouble("errorMessageSpacing", it.toDouble()) }
        brandIconMode?.let { putString("brandIconMode", it.raw) }
    }

    internal companion object {
        fun Int.toHex(): String = String.format("#%08X", 0xFFFFFFFF and this.toLong())

        /** Merges `override` on top of `base`, value-by-value. */
        fun merge(base: VaultAppearance?, override: VaultAppearance?): VaultAppearance? {
            if (base == null) return override
            if (override == null) return base
            return VaultAppearance(
                primaryColor = override.primaryColor ?: base.primaryColor,
                textColor = override.textColor ?: base.textColor,
                errorColor = override.errorColor ?: base.errorColor,
                placeholderColor = override.placeholderColor ?: base.placeholderColor,
                backgroundColor = override.backgroundColor ?: base.backgroundColor,
                borderColor = override.borderColor ?: base.borderColor,
                borderRadius = override.borderRadius ?: base.borderRadius,
                borderWidth = override.borderWidth ?: base.borderWidth,
                fontFamily = override.fontFamily ?: base.fontFamily,
                inputHeight = override.inputHeight ?: base.inputHeight,
                fontScale = override.fontScale ?: base.fontScale,
                gap = override.gap ?: base.gap,
                placeholderTextSizeAdjust =
                    override.placeholderTextSizeAdjust ?: base.placeholderTextSizeAdjust,
                errorTextSizeAdjust = override.errorTextSizeAdjust ?: base.errorTextSizeAdjust,
                errorMessageSpacing = override.errorMessageSpacing ?: base.errorMessageSpacing,
                brandIconMode = override.brandIconMode ?: base.brandIconMode,
            )
        }
    }
}
