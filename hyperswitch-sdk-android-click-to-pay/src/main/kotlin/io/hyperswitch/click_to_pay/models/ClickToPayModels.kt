package io.hyperswitch.click_to_pay.models

/**
 * Request to check if a customer has an existing Click to Pay profile
 */
data class CustomerPresenceRequest(
    val email: String? = null,
    val mobileNumber: MobileNumber? = null
)

/**
 * Mobile number details for customer identification
 */
data class MobileNumber(
    val countryCode: String,
    val phoneNumber: String
)

/**
 * Response indicating if customer has a Click to Pay profile
 */
data class CustomerPresenceResponse(
    val customerPresent: Boolean
)

/**
 * Status codes for card retrieval
 */
enum class StatusCode {
    TRIGGERED_CUSTOMER_AUTHENTICATION,
    NO_CARDS_PRESENT,
    RECOGNIZED_CARDS_PRESENT
}

/**
 * Response containing the status of card retrieval
 */
data class CardsStatusResponse(
    val statusCode: StatusCode
)

/**
 * Represents a recognized card in Click to Pay
 */
data class RecognizedCard(
    val srcDigitalCardId: String,
    val panBin: String,
    val panLastFour: String,
    val tokenLastFour: String,
    val digitalCardData: DigitalCardData,
    val panExpirationMonth: String,
    val panExpirationYear: String,
    val countryCode: String,
    val maskedBillingAddress: MaskedBillingAddress? = null
)

/**
 * Digital card metadata
 */
data class DigitalCardData(
    val status: String,
    val presentationName: String? = null,
    val descriptorName: String,
    val artUri: String
)

/**
 * Masked billing address for a card
 */
data class MaskedBillingAddress(
    val name: String? = null,
    val line1: String? = null,
    val city: String? = null,
    val state: String? = null,
    val countryCode: String? = null,
    val zip: String? = null
)

/**
 * Request to checkout with a selected card
 */
data class CheckoutRequest(
    val srcDigitalCardId: String,
    val rememberMe: Boolean = false,
)

/**
 * Response from checkout operation
 */
data class CheckoutResponse(
    val authenticationId: String,
    val merchantId: String,
    val status: String,
    val clientSecret: String,
    val amount: Int,
    val currency: String,
    val authenticationConnector: String,
    val force3dsChallenge: Boolean,
    val returnUrl: String?,
    val createdAt: String,
    val profileId: String,
    val psd2ScaExemptionType: String?,
    val acquirerDetails: AcquirerDetails?,
    val threedsServerTransactionId: String?,
    val maximumSupported3dsVersion: String?,
    val connectorAuthenticationId: String?,
    val threeDsMethodData: String?,
    val threeDsMethodUrl: String?,
    val messageVersion: String?,
    val connectorMetadata: String?,
    val directoryServerId: String?,
    val tokenData: TokenData?,
    val billing: String?,
    val shipping: String?,
    val browserInformation: String?,
    val email: String?,
    val transStatus: String,
    val acsUrl: String?,
    val challengeRequest: String?,
    val acsReferenceNumber: String?,
    val acsTransId: String?,
    val acsSignedContent: String?,
    val threeDsRequestorUrl: String?,
    val threeDsRequestorAppUrl: String?,
    val eci: String?,
    val errorMessage: String?,
    val errorCode: String?,
    val profileAcquirerId: String?
)

/**
 * Acquirer details for the transaction
 */
data class AcquirerDetails(
    val acquirerBin: String?,
    val acquirerMerchantId: String?,
    val merchantCountryCode: String?
)

/**
 * Token data returned after successful checkout
 */
data class TokenData(
    val networkToken: String,
    val cryptogram: String,
    val tokenExpirationMonth: String,
    val tokenExpirationYear: String
)
