package io.hyperswitch.authentication

/**
 * Environment constants for 3DS SDK
 */
object ThreeDSEnvironment {
    const val SANDBOX = "SANDBOX"
    const val PRODUCTION = "PRODUCTION"
    const val INTEGRATION = "INTEGRATION"
}

/**
 * Result types for authentication operations
 */
sealed class AuthenticationResult {
    data class Success(val message: String) : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
    data class Challenge(val challengeParameters: ChallengeParameters) : AuthenticationResult()
}

/**
 * Authentication Configuration containing 3DS SDK settings
 */
data class AuthenticationConfiguration(
    val threeDsSdkApiKey: String,
    val environment: String = ThreeDSEnvironment.SANDBOX,
    val uiCustomization: UiCustomization? = null
)

/**
 * UI Customization for Challenge Activity
 */
data class UiCustomization(
    val toolbarCustomization: ToolbarCustomization? = null,
    val labelCustomization: LabelCustomization? = null,
    val textBoxCustomization: TextBoxCustomization? = null,
    val buttonCustomization: ButtonCustomization? = null
)

data class ToolbarCustomization(
    val backgroundColor: String? = null,
    val headerText: String? = null,
    val buttonText: String? = null
)

data class LabelCustomization(
    val headingTextColor: String? = null,
    val headingTextFontName: String? = null,
    val headingTextFontSize: Int? = null
)

data class TextBoxCustomization(
    val textColor: String? = null,
    val textFontName: String? = null,
    val textFontSize: Int? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val cornerRadius: Int? = null
)

data class ButtonCustomization(
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val textFontName: String? = null,
    val textFontSize: Int? = null,
    val cornerRadius: Int? = null
)

/**
 * Authentication Request Parameters (AReq)
 */
data class AuthenticationRequestParameters(
    val messageVersion: String,
    val directoryServerId: String,
    val sdkTransactionId: String,
    val sdkAppId: String,
    val sdkReferenceNumber: String,
    val deviceData: String, // This will be mapped to sdk_enc_data in API call
    val sdkEphemeralPublicKey: String? = null, // For sdk_ephem_pub_key
    val sdkMaxTimeout: Int = 15 // Default timeout
)

/**
 * Challenge Parameters received from server
 */
data class ChallengeParameters(
    val transStatus: String, // "C" for Challenge, "Y" for Success, "N" for Failed
    val acsSignedContent: String,
    val acsTransactionId: String,
    val acsRefNumber: String,
    val threeDSServerTransId: String,
    val threeDSRequestorAppURL: String? = null
)

/**
 * Challenge Result
 */
data class ChallengeResult(
    val transStatus: String, // "Y" for Success, "N" for Failed, "A" for Attempt, "U" for Unavailable
    val authenticationValue: String? = null,
    val eci: String? = null,
    val errorMessage: String? = null
)

/**
 * Interface for receiving challenge status updates
 */
interface ChallengeStatusReceiver {
    fun onSuccess(result: ChallengeResult)
    fun onError(result: ChallengeResult)
    fun onTimeout()
    fun onCancel()
}
