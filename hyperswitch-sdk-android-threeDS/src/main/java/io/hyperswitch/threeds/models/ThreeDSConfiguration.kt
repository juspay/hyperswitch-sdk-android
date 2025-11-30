package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * Configuration for 3DS SDK initialization.
 */
@Keep
data class ThreeDSConfiguration(
    @Keep val environment: ThreeDSEnvironment = ThreeDSEnvironment.SANDBOX,
    @Keep val directoryServerId: String = "",
    @Keep val uiCustomization: UiCustomization? = null,
    @Keep val enableLogging: Boolean = false,
    @Keep val preferredProvider: ThreeDSProviderType? = null,
    @Keep val serverJwt: String? = null
)

/**
 * 3DS environment enumeration.
 */
@Keep
enum class ThreeDSEnvironment {
    @Keep SANDBOX,
    @Keep PRODUCTION
}
