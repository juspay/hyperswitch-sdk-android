package io.hyperswitch.paymentmethods

import android.os.Bundle

/**
 * Per-field configuration for the payment-method input widgets.
 *
 * ```
 * configuration = {
 *     appearance = {},
 *     ...any other props related to the field
 * }
 * ```
 */
data class FieldConfiguration(
    val appearance: Map<String, Any?>? = null,
    val props: Map<String, Any?>? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        appearance?.let { putBundle("appearance", BundleUtils.toBundle(it)) }
        props?.let { putAll(BundleUtils.toBundle(it)) }
    }
}
