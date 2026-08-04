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
    SESSION_NOT_FOUND,

    INSTANCE_DESTROYED,

    // Fallback
    ERROR,

    // USER ACTIONS
    CHANGE_CARD,
    SWITCH_CONSUMER;

    companion object {
        fun from(value: String?): ClickToPayErrorType {
            return try {
                ClickToPayErrorType.valueOf(value?.uppercase() ?: "ERROR")
            } catch (_: IllegalArgumentException) {
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
    errorType: ClickToPayErrorType
) : Exception(message) {
    constructor(
        message: String,
        errorType: String
    ) : this(message, ClickToPayErrorType.from(errorType))

    val type = errorType
    val reason = message
}