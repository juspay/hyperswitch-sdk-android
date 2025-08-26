package io.hyperswitch.authentication

import android.app.Activity
import android.util.Log
import io.hyperswitch.react.HyperHeadlessModule

class Transaction(
    private val activity: Activity,
    private val dsId: String,
    private val messageVersion: String,
    private var hyperHeadlessModule: HyperHeadlessModule?
) {
    
    /**
     * Generate Authentication Request Parameters
     * Obtain the authentication request parameters (aReq) required to proceed with authentication.
     * This method appears synchronous to merchants but handles callbacks internally.
     */
    fun getAuthenticationRequestParameters(): AuthenticationRequestParameters? {
        val module = hyperHeadlessModule
        if (module == null) {
            android.util.Log.e("Transaction", "HyperHeadlessModule is null. Cannot generate AReq parameters.")
            return null
        }
        
        var result: AuthenticationRequestParameters? = null
        
        // Trigger the ReScript flow to generate AReq params
        module.executeStoredGetMessageVersion()
        
        // Wait a bit for the ReScript flow to complete and store the aReqParams
        try {
            Thread.sleep(2000) // Give time for ReScript flow to complete
        } catch (e: InterruptedException) {
            android.util.Log.w("Transaction", "Sleep interrupted")
        }
        
        // Now extract the aReqParams from storedGetChallengeParamsData
        try {
            // The storedGetChallengeParamsData contains the aReqParams from ReScript
            val storedData = module.getStoredChallengeParamsData()
            if (storedData != null) {
                android.util.Log.d("Transaction", "Found stored challenge params data, extracting aReqParams")
                
                // Extract aReqParams from the stored data
                val status = storedData.getString("status") ?: ""
                if (status == "success" || status == "completed") {
                    val aReqParamsMap = storedData.getMap("aReqParams")
                    if (aReqParamsMap != null) {
                        result = AuthenticationRequestParameters(
                            messageVersion = messageVersion,
                            directoryServerId = dsId,
                            sdkTransactionId = aReqParamsMap.getString("sdkTransId") ?: "mock_sdk_transaction_id",
                            sdkAppId = aReqParamsMap.getString("sdkAppId") ?: "mock_sdk_app_id", 
                            sdkReferenceNumber = aReqParamsMap.getString("sdkReferenceNo") ?: "mock_sdk_reference_number",
                            deviceData = aReqParamsMap.getString("deviceData") ?: "mock_device_data",
                            sdkEphemeralPublicKey = aReqParamsMap.getString("sdkEphemeralKey"),
                            sdkMaxTimeout = 15
                        )
                        android.util.Log.d("Transaction", "Successfully extracted AReq params: ${result?.sdkTransactionId}")
                    } else {
                        android.util.Log.e("Transaction", "aReqParams not found in stored data")
                    }
                } else {
                    android.util.Log.e("Transaction", "AReq generation failed with status: $status")
                }
            } else {
                android.util.Log.e("Transaction", "No stored challenge params data found")
            }
        } catch (e: Exception) {
            android.util.Log.e("Transaction", "Error extracting AReq params from stored data: ${e.message}")
        }
        
        return result
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
        timeout: Int = 5,
        additionalParams: String = ""
    ) {
        val module = hyperHeadlessModule
        if (module == null) {
            android.util.Log.e("Transaction", "HyperHeadlessModule is null. Cannot perform challenge.")
            challengeStatusReceiver.onError(
                ChallengeResult(
                    transStatus = "N",
                    errorMessage = "HyperHeadlessModule not available"
                )
            )
            return
        }
        
        try {
            android.util.Log.d("Transaction", "Starting challenge flow with parameters")
            
            // First, store the challenge parameters in the module
            module.receiveChallengeParams(
                acsSignedContent = challengeParameters.acsSignedContent,
                acsTransactionId = challengeParameters.acsTransactionId,
                acsRefNumber = challengeParameters.acsRefNumber,
                threeDSServerTransId = challengeParameters.threeDSServerTransId,
                threeDSRequestorAppURL = challengeParameters.threeDSRequestorAppURL
            ) { receiveChallengeResponse ->
                android.util.Log.d("Transaction", "Challenge parameters stored, response: $receiveChallengeResponse")
                
                // Now perform the challenge - this will trigger executeStoredGetChallengeParams
                module.doChallenge(activity) { doChallengeResponse ->
                    try {
                        android.util.Log.d("Transaction", "Challenge completed, response: $doChallengeResponse")
                        
                        // Parse the doChallenge response
                        val doChallengeJson = org.json.JSONObject(doChallengeResponse)
                        val doChallengeStatus = doChallengeJson.getString("status")
                        
                        if (doChallengeStatus == "success" || doChallengeStatus == "completed") {
                            challengeStatusReceiver.onSuccess(
                                ChallengeResult(
                                    transStatus = "Y", // Success
                                    authenticationValue = "mock_authentication_value",
                                    eci = "05"
                                )
                            )
                        } else {
                            val errorMessage = doChallengeJson.optString("message", "Challenge failed")
                            challengeStatusReceiver.onError(
                                ChallengeResult(
                                    transStatus = "N", // Failed
                                    errorMessage = errorMessage
                                )
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Transaction", "Error parsing doChallenge response: ${e.message}")
                        challengeStatusReceiver.onError(
                            ChallengeResult(
                                transStatus = "N", // Failed
                                errorMessage = "Failed to parse challenge response"
                            )
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("Transaction", "Error in doChallenge: ${e.message}")
            challengeStatusReceiver.onError(
                ChallengeResult(
                    transStatus = "N", // Failed
                    errorMessage = e.message ?: "Challenge failed"
                )
            )
        }
    }
}
