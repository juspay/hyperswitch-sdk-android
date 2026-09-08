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
    val placeholder: String? = null,
    val props: Map<String, Any?>? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        appearance?.let { putBundle("appearance", BundleUtils.toBundle(it)) }
        styles?.let { putBundle("styles", BundleUtils.toBundle(it)) }
        placeholder?.let { putString("placeholder", it) }
        props?.let { putAll(BundleUtils.toBundle(it)) }
    }
}
