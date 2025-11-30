package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * Transaction request for creating a 3DS transaction.
 * This is the first step in the 3DS authentication flow.
 * Matches the merchant implementation pattern: createTransaction(directoryServerId, messageVersion, cardNetwork)
 */
@Keep
data class TransactionRequest(
    @Keep val messageVersion: String,        // From eligibility API response
    @Keep val directoryServerId: String?,    // From eligibility API response
    @Keep val cardNetwork: String?           // Merchant specifies: "VISA", "MASTERCARD", etc.
)

/**
 * Response from creating a 3DS transaction.
 */
@Keep
data class TransactionResponse(
    @Keep val transactionId: String,
    @Keep val sdkTransactionId: String,
    @Keep val serverTransactionId: String,
    @Keep val acsTransactionId: String?,
    @Keep val dsTransactionId: String?,
    @Keep val messageVersion: String,
    @Keep val deviceData: String?,
    @Keep val sdkAppId: String?,
    @Keep val sdkEncryptionData: String?,
    @Keep val sdkEphemeralPublicKey: String?,
    @Keep val sdkReferenceNumber: String?
)

/**
 * Authentication parameters returned from getAuthenticationRequestParameters.
 * These parameters are sent to the 3DS Server for authentication.
 * Note: Some fields are nullable as not all 3DS providers expose them separately.
 * For example, Cardinal bundles sdkAppId and sdkReferenceNumber in the encrypted device data.
 */
@Keep
data class AuthenticationParameters(
    @Keep val deviceData: String,
    @Keep val sdkTransactionId: String,
    @Keep val sdkAppId: String?,
    @Keep val sdkEncryptionData: String?,
    @Keep val sdkEphemeralPublicKey: String?,
    @Keep val sdkReferenceNumber: String?,
    @Keep val messageVersion: String,
    @Keep val threeDSCompInd: String?
)
