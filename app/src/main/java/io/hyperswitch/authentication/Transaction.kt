package io.hyperswitch.authentication

import android.app.Activity
import android.util.Log
import io.hyperswitch.react.HyperHeadlessModule
import org.json.JSONObject

class Transaction(
    private val activity: Activity,
    private val dsId: String,
    private val messageVersion: String,
    private var hyperHeadlessModule: HyperHeadlessModule?
) {
    
    /**
     * Generate Authentication Request Parameters (Callback-based approach)
     * Obtain the authentication request parameters (aReq) required to proceed with authentication.
     */
    fun getAuthenticationRequestParameters(callback: (AuthenticationRequestParameters?) -> Unit) {
        val module = hyperHeadlessModule
        if (module == null) {
            android.util.Log.e("Transaction", "HyperHeadlessModule is null. Cannot generate AReq parameters.")
            callback(null)
            return
        }
        
        android.util.Log.d("Transaction", "Starting AReq parameter generation using callback approach...")
        
        // Set the callback in HyperHeadlessModule so it can call us when data arrives
        module.setAuthParametersCallback { storedData ->
            android.util.Log.d("Transaction", "Callback triggered - data has arrived!")
            
            if (storedData != null) {
                val result = extractAReqParams(storedData)
                if (result != null) {
                    android.util.Log.d("Transaction", "Successfully got AReq params: ${result.sdkTransactionID}")
                } else {
                    android.util.Log.e("Transaction", "Failed to extract AReq params from stored data")
                }
                callback(result)
            } else {
                android.util.Log.e("Transaction", "Callback triggered but no stored data found")
                callback(null)
            }
        }
        
        // Trigger the ReScript flow to generate AReq params
        val success = module.executeStoredGetAuthRequestParams()
        if (!success) {
            android.util.Log.e("Transaction", "Failed to execute stored getAuthRequestParams")
            callback(null)
        }
    }
    
    /**
     * Helper method to transform ephemeral key JSON by capitalizing the keys
     * Transforms: kty -> Kty, crv -> Crv, x -> X, y -> Y
     */
    private fun transformEphemeralKeyJson(ephemeralKeyJson: String?): String? {
        if (ephemeralKeyJson.isNullOrEmpty()) {
            return ephemeralKeyJson
        }

        return try {
            val originalJson = JSONObject(ephemeralKeyJson)
            val transformedJson = JSONObject()

            // Transform keys to capitalized versions
            if (originalJson.has("kty")) {
                transformedJson.put("Kty", originalJson.getString("kty"))
            }
            if (originalJson.has("crv")) {
                transformedJson.put("Crv", originalJson.getString("crv"))
            }
            if (originalJson.has("x")) {
                transformedJson.put("X", originalJson.getString("x"))
            }
            if (originalJson.has("y")) {
                transformedJson.put("Y", originalJson.getString("y"))
            }

            val result = transformedJson.toString()
            android.util.Log.d("Transaction", "Transformed ephemeral key: $ephemeralKeyJson -> $result")
            result
        } catch (e: Exception) {
            android.util.Log.w("Transaction", "Failed to transform ephemeral key JSON: ${e.message}")
            // Return original if transformation fails
            ephemeralKeyJson
        }
    }
    
    /**
     * Helper method to extract AReq parameters from stored data
     */
    private fun extractAReqParams(storedData: com.facebook.react.bridge.ReadableMap): AuthenticationRequestParameters? {
        return try {
            val status = storedData.getString("status") ?: ""
            if (status == "success" || status == "completed") {
                val aReqParamsMap = storedData.getMap("aReqParams")
                if (aReqParamsMap != null) {
                    AuthenticationRequestParameters(
                        messageVersion = messageVersion,
//                        directoryServerId = dsId,
                        sdkTransactionID = aReqParamsMap.getString("sdkTransId") ?: "mock_sdk_transaction_id",
                        sdkAppID = aReqParamsMap.getString("sdkAppId") ?: "mock_sdk_app_id",
                        sdkReferenceNumber = aReqParamsMap.getString("sdkReferenceNo") ?: "mock_sdk_reference_number",
                        deviceData = aReqParamsMap.getString("deviceData") ?: "mock_device_data",
                        sdkEphemeralPublicKey = transformEphemeralKeyJson(aReqParamsMap.getString("sdkEphemeralKey")),
                        sdkMaxTimeout = 15
                    )
                } else {
                    android.util.Log.e("Transaction", "aReqParams not found in stored data")
                    null
                }
            } else {
                android.util.Log.e("Transaction", "AReq generation failed with status: $status")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("Transaction", "Error extracting AReq params from stored data: ${e.message}")
            null
        }
    }
    
    /**
     * Handle Challenge Authentication
     * If the transStatus indicates a challenge ("C"), initiate the challenge authentication process.
     * @param activity The current activity
     * @param challengeParameters Challenge parameters from the server
     * @param challengeStatusReceiver Callback to receive challenge status
     * @param timeout Timeout in seconds
     * @param additionalParams Additional parameters if needed
     */
    fun doChallenge(
        activity: Activity,
        challengeParameters: ChallengeParameters,
        challengeStatusReceiver: ChallengeStatusReceiver,
        timeout: Int = 5
        // additionalParams: String = "" // Currently unused parameter
    ) {
        val module = hyperHeadlessModule
        if (module == null) {
            android.util.Log.e("Transaction", "HyperHeadlessModule is null. Cannot perform challenge.")
            challengeStatusReceiver.runtimeError(
                RuntimeErrorEvent(
                    errorMessage = "HyperHeadlessModule not available"
                )
            )
            return
        }
        
        // Validate activity before proceeding
        if (activity.isFinishing || activity.isDestroyed) {
            android.util.Log.e("Transaction", "Activity is finishing or destroyed. Cannot perform challenge.")
            challengeStatusReceiver.runtimeError(
                RuntimeErrorEvent(
                    errorMessage = "Activity is not available for challenge"
                )
            )
            return
        }
        
        try {
            android.util.Log.d("Transaction", "Starting challenge flow with activity: $activity")
            android.util.Log.d("Transaction", "Challenge parameters: acsTransactionId=${challengeParameters.acsTransactionId}")
            
            // Ensure activity is set in AuthActivityManager before challenge
            io.hyperswitch.authentication.AuthActivityManager.setActivity(activity)
            android.util.Log.d("Transaction", "Activity set in AuthActivityManager: $activity")
            
            // First, store the challenge parameters in the module
            module.receiveChallengeParams(
                acsSignedContent = challengeParameters.acsSignedContent,
                acsTransactionId = challengeParameters.acsTransactionId,
                acsRefNumber = challengeParameters.acsRefNumber,
                threeDSServerTransId = challengeParameters.threeDSServerTransId,
                threeDSRequestorAppURL = challengeParameters.threeDSRequestorAppURL
            ) { receiveChallengeResponse ->
                android.util.Log.d("Transaction", "Challenge parameters stored, response: $receiveChallengeResponse")
                
                // Verify activity is still valid before proceeding
                if (activity.isFinishing || activity.isDestroyed) {
                    android.util.Log.e("Transaction", "Activity became invalid during challenge setup")
                    challengeStatusReceiver.runtimeError(
                        RuntimeErrorEvent(
                            errorMessage = "Activity became invalid during challenge setup"
                        )
                    )
                    return@receiveChallengeParams
                }
                
                // Now perform the challenge - this will trigger executeStoredsendAReqAndReceiveChallengeParams
                module.doChallenge(activity) { doChallengeResponse ->
                    try {
                        android.util.Log.d("Transaction", "Challenge completed, response: $doChallengeResponse")
                        
                        // Parse the doChallenge response - handle nested structure
                        val doChallengeJson = org.json.JSONObject(doChallengeResponse)
                        
                        // Extract the actual challenge result from nested structure
                        val dataObject = doChallengeJson.optJSONObject("data")
                        val doChallengeResult = dataObject?.optJSONObject("doChallengeResult")
                        val actualChallengeStatus = doChallengeResult?.optString("status", "")
                        
                        android.util.Log.d("Transaction", "Parsed challenge status: $actualChallengeStatus")
                        
                        if (actualChallengeStatus == "completed") {
                            challengeStatusReceiver.completed(
                                CompletionEvent(
                                    transactionId = challengeParameters.acsTransactionId,
                                    authenticationValue = "mock_authentication_value",
                                    eci = "05"
                                )
                            )
                        } else if (actualChallengeStatus == "cancelled") {
                            challengeStatusReceiver.cancelled()
                        } else if (actualChallengeStatus == "timeout") {
                            challengeStatusReceiver.timedout()
                        } else {
                            val errorMessage = doChallengeResult?.optString("message", "Challenge failed") ?: "Challenge failed"
                            android.util.Log.e("Transaction", "Challenge failed with status: $actualChallengeStatus, message: $errorMessage")
                            challengeStatusReceiver.runtimeError(
                                RuntimeErrorEvent(
                                    errorMessage = errorMessage
                                )
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Transaction", "Error parsing doChallenge response: ${e.message}")
                        challengeStatusReceiver.runtimeError(
                            RuntimeErrorEvent(
                                errorMessage = "Failed to parse challenge response: ${e.message}"
                            )
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("Transaction", "Error in doChallenge: ${e.message}")
            challengeStatusReceiver.runtimeError(
                RuntimeErrorEvent(
                    errorMessage = e.message ?: "Challenge failed"
                )
            )
        }
    }
}
