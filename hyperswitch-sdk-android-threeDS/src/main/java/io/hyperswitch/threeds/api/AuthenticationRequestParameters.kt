package io.hyperswitch.threeds.api

/**
 * Authentication request parameters for 3DS
 * Note: Some fields are nullable as not all 3DS providers expose them separately.
 * For example, Cardinal bundles sdkAppID and sdkReferenceNumber in the encrypted device data.
 */
data class AuthenticationRequestParameters(
    val sdkTransactionID: String,
    val sdkAppID: String?,
    val sdkReferenceNumber: String?,
    val sdkEphemeralPublicKey: String?,
    val deviceData: String
)
