package io.hyperswitch.paymentmethods

import android.os.Bundle

/**
 * Per-field configuration for the payment-method input widgets.
 *
 * Mirrors `fieldConfiguration` on the React Native side (`PaymentMethodTypes.res`)
 * field for field — [appearance], [styles] and [placeholder] are read from the
 * exact same `configuration.<key>` slots there, so keep the two in sync.
 *
 * ```
 * configuration = {
 *     appearance = {},
 *     styles = {},
 *     placeholder = "",
 *     ...any other props related to the field
 * }
 * ```
 */
data class FieldConfiguration(
    val appearance: Map<String, Any?>? = null,
    val styles: Map<String, Any?>? = null,
    /** Typed equivalent of [styles] — mirrors the JS `FieldStyles` (root/container/input/
     * placeholder/label/error/accessory). Merged on top of [styles] when both are given. */
    val typedStyles: FieldStyles? = null,
    /** Mirrors the JS `FieldOptions` (label, labelBehavior, errorDisplay, unstyled,
     * accessibilityLabel/Hint, cardBrandIcon, cvcIcon). */
    val options: FieldOptions? = null,
    val placeholder: String? = null,
    val props: Map<String, Any?>? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        appearance?.let { putBundle("appearance", BundleUtils.toBundle(it)) }

        val mergedStyles = buildMap<String, Any?> {
            styles?.let { putAll(it) }
            typedStyles?.toMap()?.let { putAll(it) }
        }
        if (mergedStyles.isNotEmpty()) putBundle("styles", BundleUtils.toBundle(mergedStyles))

        options?.toMap()?.takeIf { it.isNotEmpty() }
            ?.let { putBundle("options", BundleUtils.toBundle(it)) }

        placeholder?.let { putString("placeholder", it) }
        props?.let { putAll(BundleUtils.toBundle(it)) }
    }
}
