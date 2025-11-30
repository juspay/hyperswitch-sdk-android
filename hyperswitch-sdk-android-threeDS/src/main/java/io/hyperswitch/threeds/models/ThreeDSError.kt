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
) {
    companion object {
        const val INIT_FAILED = "INIT_FAILED"
        const val NOT_INITIALIZED = "NOT_INITIALIZED"
        const val SDK_NOT_READY = "SDK_NOT_READY"
        
        const val TRANSACTION_ERROR = "TRANSACTION_ERROR"
        const val TRANSACTION_FAILED = "TRANSACTION_FAILED"
        const val TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND"
        
        const val AUTH_PARAMS_ERROR = "AUTH_PARAMS_ERROR"
        const val AUTH_PARAMS_FAILED = "AUTH_PARAMS_FAILED"
        
        const val CHALLENGE_ERROR = "CHALLENGE_ERROR"
        const val CHALLENGE_TIMEOUT = "CHALLENGE_TIMEOUT"
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
        const val RUNTIME_ERROR = "RUNTIME_ERROR"
        
        const val NO_PROVIDER = "NO_PROVIDER"
        const val INIT_EXCEPTION = "INIT_EXCEPTION"
    }
}

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
