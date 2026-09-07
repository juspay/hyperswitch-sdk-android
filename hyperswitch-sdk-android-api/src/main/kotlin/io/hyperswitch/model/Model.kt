package io.hyperswitch.model

import android.os.Bundle

data class OverrideEndpoints(
    val customBackendEndpoint: String? = null,
    val customLoggingEndpoint: String? = null,
    val customAssetEndpoint: String? = null,
    val customSDKConfigEndpoint: String? = null,
    val customConfirmEndpoint: String? = null,
    val customAirborneEndpoint: String? = null,
)

data class CustomEndpointConfiguration(
    val overrideEndpoints: OverrideEndpoints? = null,
    val commonEndpoint: String? = null,
)

enum class HyperswitchEnvironment{
    PROD,
    SANDBOX,
    INTEG
}

sealed interface HyperswitchBaseConfiguration {
    val publishableKey: String?
    val profileId: String?
    val customConfig: CustomEndpointConfiguration?

    val environment : HyperswitchEnvironment?

    /**
     * Serialises this configuration into the `hyperswitchConfig` Bundle structure expected by
     * the React Native layer.
     */
    fun toBundle(): Bundle = Bundle().apply {
        putString("publishableKey", publishableKey ?: "")
        profileId?.takeIf { it.isNotEmpty() }?.let { putString("profileId", it) }
        environment?.let { putString("environment", it.name) }

        val cfg = customConfig
        if (cfg != null) {
            val oe = cfg.overrideEndpoints
            val hasAnyEndpoint = listOfNotNull(
                cfg.commonEndpoint,
                oe?.customBackendEndpoint,
                oe?.customLoggingEndpoint,
                oe?.customAssetEndpoint,
                oe?.customSDKConfigEndpoint,
                oe?.customConfirmEndpoint,
                oe?.customAirborneEndpoint,
            ).any { it.isNotEmpty() }

            if (hasAnyEndpoint) {
                putBundle("customEndpoints", Bundle().apply {
                    cfg.commonEndpoint?.takeIf { it.isNotEmpty() }
                        ?.let { putString("commonEndpoint", it) }
                    if (oe != null) {
                        putBundle("overrideEndpoints", Bundle().apply {
                            oe.customBackendEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customBackendEndpoint", it) }
                            oe.customLoggingEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customLoggingEndpoint", it) }
                            oe.customAssetEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customAssetEndpoint", it) }
                            oe.customSDKConfigEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customSDKConfigEndpoint", it) }
                            oe.customConfirmEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customConfirmEndpoint", it) }
                            oe.customAirborneEndpoint?.takeIf { it.isNotEmpty() }
                                ?.let { putString("customAirborneEndpoint", it) }
                        })
                    }
                })
            }
        }
    }
}

data class HyperswitchConfiguration(
    override val publishableKey: String? = null,
    override val profileId: String? = null,
    override val customConfig: CustomEndpointConfiguration? = null,
    override val environment: HyperswitchEnvironment? = HyperswitchEnvironment.PROD
) : HyperswitchBaseConfiguration

data class HyperswitchPlatformConfiguration(
    override val publishableKey: String? = null,
    override val profileId: String? = null,
    override val customConfig: CustomEndpointConfiguration? = null,
    val platformPublishableKey: String? = null,
    override val environment: HyperswitchEnvironment? = HyperswitchEnvironment.PROD
) : HyperswitchBaseConfiguration

data class PaymentSessionConfiguration(
    val sdkAuthorization: String,
) {
    /** Serialises this configuration into the `paymentSessionConfig` Bundle. */
    fun toBundle(): Bundle = Bundle().apply {
        putString("sdkAuthorization", sdkAuthorization)
    }
}

data class Appearance(
    val primaryColor: String? = null,
)

data class PaymentSheetConfiguration(
    val appearance: Appearance? = null,
)

