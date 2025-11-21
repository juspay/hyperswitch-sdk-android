package io.hyperswitch.threeds

import android.content.Context
import androidx.annotation.Keep
import io.hyperswitch.threeds.callbacks.AuthParametersCallback
import io.hyperswitch.threeds.callbacks.ChallengeCallback
import io.hyperswitch.threeds.callbacks.InitializationCallback
import io.hyperswitch.threeds.callbacks.TransactionCallback
import io.hyperswitch.threeds.models.*
import io.hyperswitch.threeds.provider.ProviderRegistry
import io.hyperswitch.threeds.provider.ThreeDSProvider

/**
 * Main entry point for the Hyperswitch 3DS library.
 * Provides a unified API for 3DS authentication across different providers.
 */
@Keep
class ThreeDSManager private constructor() {
    
    private var currentProvider: ThreeDSProvider? = null
    private var configuration: ThreeDSConfiguration? = null
    private var isInitialized = false
    
    companion object {
        @Volatile
        private var INSTANCE: ThreeDSManager? = null
        
        /**
         * Get the singleton instance of ThreeDSManager.
         */
        @Keep
        @JvmStatic
        fun getInstance(): ThreeDSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThreeDSManager().also { INSTANCE = it }
            }
        }
        
        /**
         * Get list of available provider types.
         */
        @Keep
        @JvmStatic
        fun getAvailableProviders(): List<io.hyperswitch.threeds.models.ThreeDSProviderType> {
            return ProviderRegistry.getAvailableProviders()
                .filter { it.isAvailable() }
                .map { it.getProviderType() }
        }
        
        /**
         * Get detailed information about available providers.
         */
        @Keep
        @JvmStatic
        fun getProviderInfo(): List<ProviderInfo> {
            return ProviderRegistry.getAvailableProviders()
                .filter { it.isAvailable() }
                .map { factory ->
                    ProviderInfo(
                        type = factory.getProviderType(),
                        version = factory.getProviderVersion(),
                        priority = factory.getPriority(),
                        supportedFeatures = factory.getSupportedFeatures()
                    )
                }
        }
    }
    
    /**
     * Initialize the 3DS manager with the given configuration.
     * This will automatically select the best available provider.
     */
    @Keep
    fun initialize(
        context: Context,
        configuration: ThreeDSConfiguration,
        callback: InitializationCallback
    ) {
        try {
            this.configuration = configuration
            
            // Select provider based on configuration
            val provider = selectProvider(configuration)
            if (provider == null) {
                callback.onInitializationFailure(
                    ThreeDSError(
                        code = ThreeDSError.NO_PROVIDER,
                        message = "No suitable 3DS provider found",
                        details = "Available providers: ${getAvailableProviders()}",
                        errorType = ErrorType.CONFIGURATION_ERROR
                    )
                )
                return
            }
            
            currentProvider = provider
            
            // Initialize the selected provider with context
            provider.initialize(context, configuration, object : InitializationCallback {
                override fun onInitializationSuccess() {
                    isInitialized = true
                    callback.onInitializationSuccess()
                }
                
                override fun onInitializationFailure(error: ThreeDSError) {
                    isInitialized = false
                    currentProvider = null
                    callback.onInitializationFailure(error)
                }
            })
            
        } catch (e: Exception) {
            callback.onInitializationFailure(
                ThreeDSError(
                    code = ThreeDSError.INIT_EXCEPTION,
                    message = "Initialization failed with exception",
                    details = e.message,
                    errorType = ErrorType.INITIALIZATION_ERROR
                )
            )
        }
    }
    
    /**
     * Create a 3DS transaction.
     * This is the first step in the 3DS authentication flow.
     */
    @Keep
    fun createTransaction(
        request: TransactionRequest,
        callback: TransactionCallback
    ) {
        val provider = currentProvider
        if (provider == null || !isInitialized) {
            callback.onTransactionFailure(
                ThreeDSError(
                    code = ThreeDSError.NOT_INITIALIZED,
                    message = "ThreeDSManager is not initialized",
                    details = "Call initialize() first",
                    errorType = ErrorType.CONFIGURATION_ERROR
                )
            )
            return
        }
        
        provider.createTransaction(request, callback)
    }
    
    /**
     * Get authentication request parameters for the transaction.
     * These parameters are sent to the 3DS Server for authentication.
     */
    @Keep
    fun getAuthenticationRequestParameters(
        transactionId: String,
        callback: AuthParametersCallback
    ) {
        val provider = currentProvider
        if (provider == null || !isInitialized) {
            callback.onAuthParametersFailure(
                ThreeDSError(
                    code = ThreeDSError.NOT_INITIALIZED,
                    message = "ThreeDSManager is not initialized",
                    details = "Call initialize() first",
                    errorType = ErrorType.CONFIGURATION_ERROR
                )
            )
            return
        }
        
        provider.getAuthenticationRequestParameters(transactionId, callback)
    }
    
    /**
     * Handle challenge flow when required by the issuer.
     * This is the 4th core function in the 3DS flow.
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
    ) {
        val provider = currentProvider
        if (provider == null || !isInitialized) {
            callback.onChallengeFailure(
                ThreeDSError(
                    code = ThreeDSError.NOT_INITIALIZED,
                    message = "ThreeDSManager is not initialized",
                    details = "Call initialize() first",
                    errorType = ErrorType.CONFIGURATION_ERROR
                )
            )
            return
        }
        
        provider.doChallenge(activity, parameters, timeout, callback)
    }
    
    
    /**
     * Get the currently active provider type.
     */
    @Keep
    fun getCurrentProvider(): io.hyperswitch.threeds.models.ThreeDSProviderType? {
        return currentProvider?.getProviderType()
    }
    
    /**
     * Get the currently active provider instance.
     * This is used internally by the unified API for provider-specific operations.
     */
    @Keep
    fun getCurrentProviderInstance(): ThreeDSProvider? {
        return currentProvider
    }
    
    /**
     * Check if the manager is initialized and ready to use.
     */
    @Keep
    fun isInitialized(): Boolean {
        return isInitialized && currentProvider?.isInitialized() == true
    }
    
    /**
     * Clean up resources and reset the manager.
     */
    @Keep
    fun cleanup() {
        currentProvider?.cleanup()
        currentProvider = null
        configuration = null
        isInitialized = false
    }
    
    /**
     * Select the best provider based on configuration and availability.
     */
    private fun selectProvider(configuration: ThreeDSConfiguration): ThreeDSProvider? {
        val availableFactories = ProviderRegistry.getAvailableProviders()
            .filter { it.isAvailable() && it.supportsConfiguration(configuration) }
        
        if (availableFactories.isEmpty()) {
            return null
        }
        
        // If preferred provider is specified, try to use it
        configuration.preferredProvider?.let { preferredType ->
            availableFactories.find { it.getProviderType() == preferredType }
                ?.let { return it.createProvider() }
        }
        
        // Otherwise, select by priority
        return availableFactories
            .maxByOrNull { it.getPriority() }
            ?.createProvider()
    }
}

/**
 * Information about a 3DS provider.
 */
@Keep
data class ProviderInfo(
    @Keep val type: io.hyperswitch.threeds.models.ThreeDSProviderType,
    @Keep val version: String,
    @Keep val priority: Int,
    @Keep val supportedFeatures: List<String>
)
