package io.hyperswitch.model

sealed class CustomEndpointConfiguration {
    data class CustomEndpoint(val url: String) : CustomEndpointConfiguration()
    data class OverrideEndpoints(
        val backendEndpoint: String? = null,
        val assetsEndpoint: String? = null,
        val sdkConfigEndpoint: String? = null,
        val confirmEndpoint: String? = null,
        val airborneEndpoint: String? = null,
        val loggingEndpoint: String? = null,
    ) : CustomEndpointConfiguration()
}

enum class HyperswitchEnvironment {
    PROD,
    SANDBOX,
    INTEG
}

sealed interface HyperswitchBaseConfiguration {
    val publishableKey: String?
    val profileId: String?
    val customConfig: CustomEndpointConfiguration?
    val environment: HyperswitchEnvironment?
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
)

data class Appearance(
    val primaryColor: String? = null,
)

data class PaymentSheetConfiguration(
    val appearance: Appearance? = null,
)

