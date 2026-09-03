package io.hyperswitch.paymentmethods

import android.os.Bundle

/**
 * Configuration for [PaymentMethodSession].
 *
 * Mirrors the merchant-facing config object:
 * ```
 * configObject = {
 *     vault_type = "",
 *     vault_data = "data"
 * }
 * ```
 */
data class PaymentMethodSessionConfiguration(
    val vaultType: String? = null,
    val vaultData: String? = null,
    val props: Map<String, Any?> = emptyMap(),
) {
    /** Serialises this configuration into the wire format expected by the React Native layer. */
    fun toBundle(): Bundle = Bundle().apply {
        vaultType?.let { putString("vault_type", it) }
        vaultData?.let { putString("vault_data", it) }
        putAll(BundleUtils.toBundle(props))
    }
}
