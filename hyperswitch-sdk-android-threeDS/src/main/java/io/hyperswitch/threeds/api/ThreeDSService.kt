package io.hyperswitch.threeds.api

import androidx.annotation.Keep
import io.hyperswitch.threeds.ThreeDSManager
import io.hyperswitch.threeds.callbacks.TransactionCallback
import io.hyperswitch.threeds.models.ThreeDSError
import io.hyperswitch.threeds.models.TransactionRequest

/**
 * 3DS Service for creating transactions.
 * Returned after successful SDK initialization.
 * Use this to create 3DS transactions.
 */
@Keep
class ThreeDSService internal constructor(
    private val threeDSManager: ThreeDSManager
) {
    
    /**
     * @param directoryServerId The directory server ID (optional)
     * @param messageVersion The 3DS message version (e.g., "2.2.0")
     * @param cardNetwork The card network (e.g., "VISA", "MASTERCARD")
     * @param callback Callback with ThreeDSResult
     */
    @Keep
    fun createTransaction(
        directoryServerId: String?,
        messageVersion: String,
        cardNetwork: String,
        callback: (ThreeDSResult<ThreeDSTransaction>) -> Unit
    ) {
        val transactionRequest = TransactionRequest(
            messageVersion = messageVersion,
            directoryServerId = directoryServerId,
            cardNetwork = cardNetwork
        )
        
        threeDSManager.createTransaction(transactionRequest, object : TransactionCallback {
            override fun onTransactionSuccess(response: io.hyperswitch.threeds.models.TransactionResponse) {
                val transaction = ThreeDSTransaction(response.transactionId, threeDSManager)
                callback(ThreeDSResult.Success(transaction))
            }
            
            override fun onTransactionFailure(error: ThreeDSError) {
                callback(ThreeDSResult.Error(error.message, error))
            }
        })
    }
}
