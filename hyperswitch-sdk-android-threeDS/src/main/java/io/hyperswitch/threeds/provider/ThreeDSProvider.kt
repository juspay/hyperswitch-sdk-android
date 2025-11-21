package io.hyperswitch.threeds.provider

import androidx.annotation.Keep
import io.hyperswitch.threeds.callbacks.AuthParametersCallback
import io.hyperswitch.threeds.callbacks.ChallengeCallback
import io.hyperswitch.threeds.callbacks.InitializationCallback
import io.hyperswitch.threeds.callbacks.TransactionCallback
import io.hyperswitch.threeds.models.ChallengeParameters
import io.hyperswitch.threeds.models.ThreeDSConfiguration
import io.hyperswitch.threeds.models.ThreeDSProviderType
import io.hyperswitch.threeds.models.TransactionRequest

/**
 * Core interface that all 3DS providers must implement.
 * This provides a unified API for different 3DS SDKs (Netcetera, Cardinal Commerce, etc.)
 */
@Keep
interface ThreeDSProvider {
    
    /**
     * Initialize the 3DS provider with the given configuration.
     * This should be called before any other operations.
     */
    @Keep
    fun initialize(
        context: android.content.Context,
        configuration: ThreeDSConfiguration,
        callback: InitializationCallback
    )
    
    /**
     * Check if the provider is properly initialized and ready to use.
     */
    @Keep
    fun isInitialized(): Boolean
    
    /**
     * Get the SDK version of the underlying 3DS implementation.
     */
    @Keep
    fun getSDKVersion(): String
    
    /**
     * Get the provider type (e.g., TRIDENT, NETCETERA, CARDINAL).
     */
    @Keep
    fun getProviderType(): ThreeDSProviderType
    
    /**
     * Create a 3DS transaction.
     * This is the first step in the 3DS authentication flow.
     */
    @Keep
    fun createTransaction(
        request: TransactionRequest,
        callback: TransactionCallback
    )
    
    /**
     * Get authentication request parameters for the transaction.
     * These parameters are sent to the 3DS Server for authentication.
     */
    @Keep
    fun getAuthenticationRequestParameters(
        transactionId: String,
        callback: AuthParametersCallback
    )
    
    /**
     * Handle challenge flow when required by the issuer.
     * This presents the challenge UI to the user.
     * Renamed from handleChallenge to match the 4 core functions pattern.
     * 
     * @param activity Activity context for showing challenge UI
     * @param parameters Challenge parameters from backend
     * @param timeout Timeout in minutes (minimum 5)
     * @param callback Challenge completion callback
     */
    @Keep
    fun doChallenge(
        activity: android.app.Activity,
        parameters: ChallengeParameters,
        timeout: Int,
        callback: ChallengeCallback
    )
    
    /**
     * Clean up resources and reset the provider state.
     */
    @Keep
    fun cleanup()
    
    /**
     * Check if the provider supports the given configuration.
     * Used for provider selection and validation.
     */
    @Keep
    fun supportsConfiguration(configuration: ThreeDSConfiguration): Boolean
}
