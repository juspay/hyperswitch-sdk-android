package io.hyperswitch.vault.core

import android.os.Bundle

/**
 * Merchant per-field options (the React Native vault's `fieldOptions` /
 * `cardNumberOptions` / `cvcOptions` / `expiryOptions` contract).
 *
 * `appearance` decides *how the field looks*; `options` decides *which
 * visual elements exist*. Per-field extras (`brandIconMode` is card-number
 * only, `cvcIcon` is cvc only) are simply absent from the Bundle when not
 * set — the JS side owns the defaults.
 */
data class VaultFieldOptions(
    val placeholder: String? = null,
    val label: String? = null,
    val labelBehavior: LabelBehavior? = null,
    val errorDisplay: ErrorDisplay? = null,
    val accessibilityLabel: String? = null,
    val accessibilityHint: String? = null,
    val testID: String? = null,
    /** Card-number field only. Falls back to `appearance.brandIconMode`, then `hidden`. */
    val brandIconMode: VaultAppearance.BrandIconMode? = null,
    /** CVC field only. */
    val cvcIcon: CvcIcon? = null,
    /**
     * Renders the field as a bare input — no label, icons, box or inline
     * error UI. Wins over the other options; accessibility text and masking
     * survive. Technical details in the RN vault's field-options docs.
     */
    val unstyled: Boolean? = null,
    /** Per-slot styles; a style never turns an element on. */
    val styles: VaultFieldStyles? = null,
) {
    enum class LabelBehavior(val raw: String) {
        NONE("none"),
        STATIC("static"),
        FLOATING("floating"),
    }

    enum class ErrorDisplay(val raw: String) {
        NONE("none"),
        INLINE("inline"),
    }

    enum class CvcIcon(val raw: String) {
        NONE("none"),
        DEFAULT("default"),
    }

    internal fun toBundle(): Bundle = Bundle().apply {
        placeholder?.let { putString("placeholder", it) }
        label?.let { putString("label", it) }
        labelBehavior?.let { putString("labelBehavior", it.raw) }
        errorDisplay?.let { putString("errorDisplay", it.raw) }
        accessibilityLabel?.let { putString("accessibilityLabel", it) }
        accessibilityHint?.let { putString("accessibilityHint", it) }
        testID?.let { putString("testID", it) }
        brandIconMode?.let { putString("brandIconMode", it.raw) }
        cvcIcon?.let { putString("cvcIcon", it.raw) }
        unstyled?.let { putBoolean("unstyled", it) }
        styles?.takeUnless { it.isEmpty }?.let { putBundle("styles", it.toBundle()) }
    }

    internal companion object {
        /** Merges `override` on top of `base`, value-by-value. */
        fun merge(base: VaultFieldOptions?, override: VaultFieldOptions?): VaultFieldOptions? {
            if (base == null) return override
            if (override == null) return base
            return VaultFieldOptions(
                placeholder = override.placeholder ?: base.placeholder,
                label = override.label ?: base.label,
                labelBehavior = override.labelBehavior ?: base.labelBehavior,
                errorDisplay = override.errorDisplay ?: base.errorDisplay,
                accessibilityLabel = override.accessibilityLabel ?: base.accessibilityLabel,
                accessibilityHint = override.accessibilityHint ?: base.accessibilityHint,
                testID = override.testID ?: base.testID,
                brandIconMode = override.brandIconMode ?: base.brandIconMode,
                cvcIcon = override.cvcIcon ?: base.cvcIcon,
                unstyled = override.unstyled ?: base.unstyled,
                styles = VaultFieldStyles.merge(base.styles, override.styles),
            )
        }
    }
}
