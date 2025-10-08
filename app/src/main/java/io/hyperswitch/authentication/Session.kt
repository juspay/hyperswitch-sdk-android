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
    private var messageVersion: String = "2.2.0" // Default message version
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
    fun getAuthRequestParams(): String {
        return messageVersion
    }

    fun getCardNetwork(): String {
        return cardNetwork
    }

    /**
     * Create a transaction for authentication
     * @param dsId Directory Server ID (optional, defaults to Visa DS ID)
     * @param messageVersion Message Version (optional, defaults to "2.3.1")
     * @param cardNetwork Card Network (optional, defaults to "VISA")
     * @return Transaction object
     */
    fun createTransaction(
        dsId: String? = null,
        messageVersion: String? = null,
        cardNetwork: String? = null
    ): Transaction {
        this.directoryServerID = dsId ?: this.directoryServerID
        this.messageVersion = messageVersion ?: this.messageVersion
        this.cardNetwork = cardNetwork ?: this.cardNetwork

        // Get the HyperHeadlessModule instance when creating transaction
        // This ensures React Native context is ready
        val currentModule = hyperHeadlessModule ?: HyperHeadlessModule.getInstance()
        
        if (currentModule == null) {
            Log.w(TAG, "HyperHeadlessModule not available when creating transaction. React Native context may not be ready yet.")
        }

        return Transaction(
            activity = activity,
            dsId = this.directoryServerID,
            messageVersion = this.messageVersion,
            hyperHeadlessModule = currentModule
        )
    }
    

}
