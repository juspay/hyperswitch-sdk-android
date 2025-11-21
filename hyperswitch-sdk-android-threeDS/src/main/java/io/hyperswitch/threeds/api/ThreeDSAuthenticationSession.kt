package io.hyperswitch.threeds.api

import android.app.Activity
import androidx.annotation.Keep
import io.hyperswitch.threeds.ThreeDSManager
import io.hyperswitch.threeds.callbacks.InitializationCallback
import io.hyperswitch.threeds.models.ThreeDSConfiguration
import io.hyperswitch.threeds.models.ThreeDSError

/**
 * Main entry point for 3DS authentication.
 * Merchants create this with their Activity and API key, then initialize a session.
 */
@Keep
class ThreeDSAuthenticationSession(
    private val activity: Activity,
    private val apiKey: String
) {
    
    private val threeDSManager = ThreeDSManager.getInstance()
    
    /**
     * Initialize a 3DS session with the given configuration.
     * 
     * @param clientSecret The client secret for authentication
     * @param configuration The 3DS configuration
     * @param callback Callback with ThreeDSResult containing the initialized service
     *
     */
    @Keep
    fun initThreeDsSession(
        clientSecret: String,
        configuration: ThreeDSConfiguration,
        callback: (ThreeDSResult<ThreeDSService>) -> Unit
    ) {
        threeDSManager.initialize(activity, configuration, object : InitializationCallback {
            override fun onInitializationSuccess() {
                val service = ThreeDSService(threeDSManager)
                callback(ThreeDSResult.Success(service))
            }
            
            override fun onInitializationFailure(error: ThreeDSError) {
                callback(ThreeDSResult.Error(error.message, error))
            }
        })
    }
}
