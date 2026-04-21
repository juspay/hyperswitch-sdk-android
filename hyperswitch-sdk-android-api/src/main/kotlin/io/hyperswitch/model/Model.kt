package io.hyperswitch.model

data class CustomEndpointConfiguration(
    val customEndpoint: String? = null,
    val overrideCustomBackendEndpoint: String? = null,
    val overrideCustomAssetsEndpoint: String? = null,
    val overrideCustomSDKConfigEndpoint: String? = null,
    val overrideCustomConfirmEndpoint: String? = null,
    val overrideCustomAirborneEndpoint: String? = null,
    val overrideCustomLoggingEndpoint: String? = null,
)

sealed interface HyperswitchBaseConfiguration {
    val publishableKey: String?
    val profileId: String?
    val customConfig: CustomEndpointConfiguration?
}

data class HyperswitchConfiguration(
    override val publishableKey: String? = null,
    override val profileId: String? = null,
    override val customConfig: CustomEndpointConfiguration? = null,

) : HyperswitchBaseConfiguration

data class HyperswitchPlatformConfiguration(
    override val publishableKey: String? = null,
    override val profileId: String? = null,
    override val customConfig: CustomEndpointConfiguration? = null,
    val platformPublishableKey: String? = null,
) : HyperswitchBaseConfiguration

data class PaymentSessionConfiguration(
    val sdkAuthorization: String,
)

data class Appearance(
    val primaryColor: String? = null,
)

data class PaymentSheetConfiguration(
    val appearance: Appearance? = null,
)