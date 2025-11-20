package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * Error information for 3DS operations.
 */
@Keep
data class ThreeDSError(
    @Keep val code: String,
    @Keep val message: String,
    @Keep val details: String?,
    @Keep val errorType: ErrorType,
    @Keep val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of 3DS errors.
 */
@Keep
enum class ErrorType {
    @Keep INITIALIZATION_ERROR,
    @Keep AUTHENTICATION_ERROR,
    @Keep CHALLENGE_ERROR,
    @Keep NETWORK_ERROR,
    @Keep CONFIGURATION_ERROR,
    @Keep PROVIDER_ERROR,
    @Keep TIMEOUT_ERROR,
    @Keep USER_CANCELLED,
    @Keep UNKNOWN_ERROR
}
