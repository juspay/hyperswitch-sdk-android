package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * Enum representing available 3DS provider types.
 * 
 * Example:
 * ```
 * val config = ThreeDSConfiguration.Builder()
 *     .setPreferredProvider(ThreeDSProviderType.TRIDENT)
 *     .build()
 * ```
 */
@Keep
enum class ThreeDSProviderType {
    @Keep TRIDENT,
    @Keep NETCETERA,
    @Keep CARDINAL
}
