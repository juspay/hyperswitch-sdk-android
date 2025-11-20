package io.hyperswitch.threeds.callbacks

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.AuthenticationParameters
import io.hyperswitch.threeds.models.ThreeDSError

/**
 * Callback interface for getting authentication request parameters.
 */
@Keep
interface AuthParametersCallback {
    
    /**
     * Called when authentication parameters are successfully retrieved.
     */
    @Keep
    fun onAuthParametersSuccess(parameters: AuthenticationParameters)
    
    /**
     * Called when getting authentication parameters fails.
     */
    @Keep
    fun onAuthParametersFailure(error: ThreeDSError)
}
