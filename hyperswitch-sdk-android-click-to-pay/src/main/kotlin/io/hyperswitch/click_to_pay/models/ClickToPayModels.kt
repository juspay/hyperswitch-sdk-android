package io.hyperswitch.click_to_pay.models

/**
 * Error types for Click to Pay operations
 */
enum class ClickToPayErrorType {
    // getUserType errors
    AUTH_INVALID,
    ACCT_INACCESSIBLE,
    ACCT_FRAUD,
    CONSUMER_ID_MISSING,
    CONSUMER_ID_FORMAT_UNSUPPORTED,
    CONSUMER_ID_FORMAT_INVALID,

    // validateCustomerAuthentication errors
    OTP_SEND_FAILED,
    VALIDATION_DATA_MISSING,
    VALIDATION_DATA_EXPIRED,
    VALIDATION_DATA_INVALID,
    RETRIES_EXCEEDED,

    // Standard errors
    UNKNOWN_ERROR,
    REQUEST_TIMEOUT,
    SERVER_ERROR,
    INVALID_PARAMETER,
    INVALID_REQUEST,
    AUTH_ERROR,
    NOT_FOUND,
    RATE_LIMIT_EXCEEDED,
    SERVICE_ERROR,

    // SDK errors
    SCRIPT_LOAD_ERROR,
    HYPER_UNDEFINED_ERROR,
    HYPER_INITIALIZATION_ERROR,
    INIT_CLICK_TO_PAY_SESSION_ERROR,
    IS_CUSTOMER_PRESENT_ERROR,
    GET_RECOGNIZED_CARDS_ERROR,
    CHECKOUT_WITH_CARD_ERROR,

    // Fallback
    ERROR,

    // USER ACTIONS
    CHANGE_CARD,
    SWITCH_CONSUMER;

    companion object {
        fun from(value: String?): ClickToPayErrorType {
            return try {
                ClickToPayErrorType.valueOf(value?.uppercase() ?: "ERROR")
            } catch (_ : IllegalArgumentException) {
                ERROR
            }
        }
    }
}

/**
 * Click to Pay exception with error details
 */
class ClickToPayException(
    message: String,
    val errorType: String
) : Exception("ClickToPay: $errorType: $message"){
    val reason = message
    val type = ClickToPayErrorType.from(errorType)
}

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
    RECOGNIZED_CARDS_PRESENT;

    companion object {
        fun from(value: String?): StatusCode {
            return try {
                StatusCode.valueOf(value?.uppercase() ?: "NO_CARDS_PRESENT")
            } catch (_ : IllegalArgumentException) {
                NO_CARDS_PRESENT
            }
        }
    }
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
    val panBin: String? = null,
    val panLastFour: String? = null,
    val panExpirationMonth: String? = null,
    val panExpirationYear: String? = null,
    val tokenLastFour: String? = null,
    val tokenBinRange: String? = null,
    val digitalCardData: DigitalCardData? = null,
    val countryCode: String? = null,
    val maskedBillingAddress: MaskedBillingAddress? = null,
    val dateOfCardCreated: String? = null,
    val dateOfCardLastUsed: String? = null,
    val paymentAccountReference: String? = null,
    val paymentCardDescriptor: CardType? = null,
    val paymentCardType: String? = null,
    val dcf: DCF? = null,
    val digitalCardFeatures: Map<String, Any>? = null
)

/**
 * Digital card metadata
 */
data class DigitalCardData(
    val status: String? = null,
    val presentationName: String? = null,
    val descriptorName: String? = null,
    val artUri: String? = null,
    val artHeight: Int? = null,
    val artWidth: Int? = null,
    val authenticationMethods: List<AuthenticationMethod>? = null,
    val pendingEvents: List<String>? = null
)

/**
 * Authentication method for digital card
 */
data class AuthenticationMethod(
    val authenticationMethodType: String
)

/**
 * DCF (Digital Card Facilitator) information
 */
data class DCF(
    val name: String? = null,
    val uri: String? = null,
    val logoUri: String? = null
)

/**
 * Masked billing address for a card
 */
data class MaskedBillingAddress(
    val addressId: String? = null,
    val name: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val line3: String? = null,
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
 * Authentication status enum
 */
enum class AuthenticationStatus {
    SUCCESS,
    FAILED,
    PENDING
}

/**
 * Response from checkout operation
 */
data class CheckoutResponse(
    val authenticationId: String?,
    val merchantId: String?,
    val status: AuthenticationStatus?,
    val clientSecret: String?,
    val amount: Int?,
    val currency: String?,
    val authenticationConnector: String?,
    val force3dsChallenge: Boolean = false,
    val returnUrl: String?,
    val createdAt: String?,
    val profileId: String?,
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
    val vaultTokenData: PaymentData?,
    val paymentMethodData: PaymentData?,
    val billing: String?,
    val shipping: String?,
    val browserInformation: String?,
    val email: String?,
    val transStatus: String?,
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
 * Vault and Payment Method data type enum
 */
enum class DataType {
    CARD_DATA,
    NETWORK_TOKEN_DATA
}

/**
 * Payment Method Data and Vault token data returned after successful checkout
 */

sealed class PaymentData {
    abstract val type: DataType

    data class CardData(
        override val type: DataType = DataType.CARD_DATA,
        val cardNumber: String? = null,
        val cardCvc: String? = null,
        val cardExpiryMonth: String? = null,
        val cardExpiryYear: String? = null
    ) : PaymentData()

    data class NetworkTokenData(
        override val type: DataType = DataType.NETWORK_TOKEN_DATA,
        val networkToken: String? = null,
        val networkTokenCryptogram: String? = null,
        val networkTokenExpiryMonth: String? = null,
        val networkTokenExpiryYear: String? = null
    ) : PaymentData()
}

enum class CardType {
    VISA,
    MASTERCARD,
    UNKNOWN;

    companion object {
        fun from(value: String?): CardType {
            return try {
                valueOf(value?.uppercase() ?: "UNKNOWN")
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}

data class SignOutResponse(
    val recognized: Boolean? = false
)