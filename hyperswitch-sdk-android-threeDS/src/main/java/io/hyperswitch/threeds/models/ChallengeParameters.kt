package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * Parameters for challenge flow presentation.
 */
@Keep
data class ChallengeParameters(
    @Keep val threeDSServerTransactionId: String,
    @Keep val acsTransactionId: String,
    @Keep val acsReferenceNumber: String,
    @Keep val acsSignedContent: String,
    @Keep val challengeWindowSize: ChallengeWindowSize = ChallengeWindowSize.FULL_SCREEN,
    @Keep val threeDSRequestorAppURL: String? = null,
    @Keep val transStatus: String
)

/**
 * Result of challenge flow completion.
 */
@Keep
data class ChallengeResult(
    @Keep val transactionId: String,
    @Keep val status: AuthenticationStatus,
    @Keep val authenticationValue: String?,
    @Keep val eci: String?,
    @Keep val timestamp: Long = System.currentTimeMillis()
)

/**
 * Challenge window size options.
 */
@Keep
enum class ChallengeWindowSize {
    @Keep FULL_SCREEN,
    @Keep EXTRA_SMALL,
    @Keep SMALL,
    @Keep MEDIUM,
    @Keep LARGE
}

/**
 * Authentication status values per EMVCo 3DS spec.
 */
@Keep
enum class AuthenticationStatus {
    @Keep Y,  // Authentication successful
    @Keep N,  // Not authenticated
    @Keep U,  // Unable to authenticate
    @Keep A,  // Attempts processing performed
    @Keep C,  // Challenge required
    @Keep R   // Rejected
}
