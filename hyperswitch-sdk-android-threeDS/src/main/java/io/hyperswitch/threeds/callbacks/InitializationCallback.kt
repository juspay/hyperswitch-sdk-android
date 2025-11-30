package io.hyperswitch.threeds.callbacks

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ThreeDSError

/**
 * Callback interface for 3DS provider initialization.
 */
@Keep
interface InitializationCallback {
    
    /**
     * Called when the provider initialization is successful.
     */
    @Keep
    fun onInitializationSuccess()
    
    /**
     * Called when the provider initialization fails.
     * 
     * @param error Details about the initialization failure
     */
    @Keep
    fun onInitializationFailure(error: ThreeDSError)
}
