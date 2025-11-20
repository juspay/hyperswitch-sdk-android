package io.hyperswitch.threeds.callbacks

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ThreeDSError
import io.hyperswitch.threeds.models.TransactionResponse

/**
 * Callback interface for transaction creation operations.
 */
@Keep
interface TransactionCallback {
    
    /**
     * Called when transaction creation is successful.
     */
    @Keep
    fun onTransactionSuccess(response: TransactionResponse)
    
    /**
     * Called when transaction creation fails.
     */
    @Keep
    fun onTransactionFailure(error: ThreeDSError)
}
