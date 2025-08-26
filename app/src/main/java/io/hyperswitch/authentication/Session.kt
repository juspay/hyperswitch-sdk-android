package io.hyperswitch.authentication

import android.app.Activity
import android.util.Log
import io.hyperswitch.react.HyperHeadlessModule

class Session(
    private val activity: Activity,
    private val publishableKey: String,
    private val paymentIntentClientSecret: String,
    private val uiCustomization: UiCustomization?,
    private var hyperHeadlessModule: HyperHeadlessModule?
) {
    private var directoryServerID: String = "A000000004" // Default Visa DS ID
    private var messageVersion: String = "2.3.1" // Default message version
    private var cardNetwork: String = "VISA" // Default card network
    
    companion object {
        private const val TAG = "Session"
    }

    /**
     * Get Directory Server ID for the authentication transaction
     * @return Directory Server ID
     */
    fun getDirectoryServerID(): String {
        return directoryServerID
    }
    
    /**
     * Get Message Version for the authentication transaction
     * @return Message Version
     */
    fun getMessageVersion(): String {
        return messageVersion
    }

    fun getCardNetwork(): String {
        return cardNetwork
    }

    /**
     * Create a transaction for authentication
     * @param dsId Directory Server ID
     * @param messageVersion Message Version
     * @return Transaction object
     */
    fun createTransaction(
        dsId: String,
        messageVersion: String,
        cardNetwork: String
    ): Transaction {
        this.directoryServerID = dsId
        this.messageVersion = messageVersion
        this.cardNetwork = cardNetwork

        // Get the HyperHeadlessModule instance when creating transaction
        // This ensures React Native context is ready
        val currentModule = hyperHeadlessModule ?: HyperHeadlessModule.getInstance()
        
        if (currentModule == null) {
            Log.w(TAG, "HyperHeadlessModule not available when creating transaction. React Native context may not be ready yet.")
        }

        return Transaction(
            activity = activity,
            dsId = dsId,
            messageVersion = messageVersion,
            hyperHeadlessModule = currentModule
        )
    }
    
    /**
     * Accept Challenge Parameters from merchant
     * Merchants should make their own API calls and pass the challenge parameters to this method
     * @param challengeParameters Challenge parameters received from merchant's API calls
     * @return Challenge Parameters
     */
    fun getChallengeParameters(challengeParameters: ChallengeParameters): ChallengeParameters {
        Log.d(TAG, "Received challenge parameters from merchant. Status: ${challengeParameters.transStatus}")
        return challengeParameters
    }

}
