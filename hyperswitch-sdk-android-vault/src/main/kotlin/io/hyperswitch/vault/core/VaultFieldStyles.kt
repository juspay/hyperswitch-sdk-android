package io.hyperswitch.vault.core

import android.os.Bundle
import androidx.annotation.ColorInt

/**
 * A view-style slot of [VaultFieldStyles]. Typed subset of the React Native
 * `ViewStyle` keys the JS vault surfaces accept under
 * `configuration.options.styles.{root,container,accessory}` — keys not listed
 * here are deliberately not settable from native.
 *
 * Numeric values are dp; colors are converted with the same alpha-first hex
 * the appearance tokens use (see VaultAppearance.toHex).
 */
data class VaultViewStyle(
    @param:ColorInt val backgroundColor: Int? = null,
    val width: Float? = null,
    val height: Float? = null,
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val marginStart: Float? = null,
    val marginEnd: Float? = null,
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        backgroundColor?.let { putString("backgroundColor", toHex(it)) }
        width?.let { putDouble("width", it.toDouble()) }
        height?.let { putDouble("height", it.toDouble()) }
        marginTop?.let { putDouble("marginTop", it.toDouble()) }
        marginBottom?.let { putDouble("marginBottom", it.toDouble()) }
        marginStart?.let { putDouble("marginStart", it.toDouble()) }
        marginEnd?.let { putDouble("marginEnd", it.toDouble()) }
    }

    internal val isEmpty: Boolean
        get() = backgroundColor == null && width == null && height == null &&
                marginTop == null && marginBottom == null &&
                marginStart == null && marginEnd == null

    internal companion object {
        fun toHex(@ColorInt color: Int): String =
            String.format("#%08X", 0xFFFFFFFF and color.toLong())
    }
}

/**
 * A text-style slot of [VaultFieldStyles]. Typed subset of the React Native
 * `TextStyle` keys accepted under
 * `configuration.options.styles.{input,placeholder,label,error}`.
 *
 * [fontWeight] takes a React Native weight string: "normal", "bold" or a
 * hundred-step value "100"…"900". [textAlign] takes "auto", "left", "right",
 * "center" or "justify".
 */
data class VaultTextStyle(
    @param:ColorInt val color: Int? = null,
    @param:ColorInt val backgroundColor: Int? = null,
    val fontSize: Float? = null,
    val fontFamily: String? = null,
    val fontWeight: String? = null,
    val letterSpacing: Float? = null,
    val textAlign: String? = null,
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        color?.let { putString("color", VaultViewStyle.toHex(it)) }
        backgroundColor?.let { putString("backgroundColor", VaultViewStyle.toHex(it)) }
        fontSize?.let { putDouble("fontSize", it.toDouble()) }
        fontFamily?.let { putString("fontFamily", it) }
        fontWeight?.let { putString("fontWeight", it) }
        letterSpacing?.let { putDouble("letterSpacing", it.toDouble()) }
        textAlign?.let { putString("textAlign", it) }
    }

    internal val isEmpty: Boolean
        get() = color == null && backgroundColor == null && fontSize == null &&
                fontFamily == null && fontWeight == null && letterSpacing == null &&
                textAlign == null
}

/**
 * Per-slot styles for one vault field — the React Native vault's
 * `CardFieldStyles.fieldStyles` contract, travelling under
 * `configuration.options.styles`.
 *
 * Field **options** decide whether an element exists; these styles decide how
 * it looks — setting a style slot never turns the element on. `accessory` is
 * the brand-icon/glyph container; the expiry field has no accessory element.
 */
data class VaultFieldStyles(
    val root: VaultViewStyle? = null,
    val container: VaultViewStyle? = null,
    val input: VaultTextStyle? = null,
    val placeholder: VaultTextStyle? = null,
    val label: VaultTextStyle? = null,
    val error: VaultTextStyle? = null,
    val accessory: VaultViewStyle? = null,
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        root?.takeUnless { it.isEmpty }?.let { putBundle("root", it.toBundle()) }
        container?.takeUnless { it.isEmpty }?.let { putBundle("container", it.toBundle()) }
        input?.takeUnless { it.isEmpty }?.let { putBundle("input", it.toBundle()) }
        placeholder?.takeUnless { it.isEmpty }?.let { putBundle("placeholder", it.toBundle()) }
        label?.takeUnless { it.isEmpty }?.let { putBundle("label", it.toBundle()) }
        error?.takeUnless { it.isEmpty }?.let { putBundle("error", it.toBundle()) }
        accessory?.takeUnless { it.isEmpty }?.let { putBundle("accessory", it.toBundle()) }
    }

    internal val isEmpty: Boolean
        get() = (root?.isEmpty ?: true) && (container?.isEmpty ?: true) &&
                (input?.isEmpty ?: true) && (placeholder?.isEmpty ?: true) &&
                (label?.isEmpty ?: true) && (error?.isEmpty ?: true) &&
                (accessory?.isEmpty ?: true)

    internal companion object {
        /** Merges `override` on top of `base`, slot-by-slot. */
        fun merge(base: VaultFieldStyles?, override: VaultFieldStyles?): VaultFieldStyles? {
            if (base == null) return override
            if (override == null) return base
            return VaultFieldStyles(
                root = override.root ?: base.root,
                container = override.container ?: base.container,
                input = override.input ?: base.input,
                placeholder = override.placeholder ?: base.placeholder,
                label = override.label ?: base.label,
                error = override.error ?: base.error,
                accessory = override.accessory ?: base.accessory,
            )
        }
    }
}
