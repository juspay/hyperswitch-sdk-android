package io.hyperswitch.threeds.provider

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ThreeDSConfiguration
import io.hyperswitch.threeds.models.ThreeDSProviderType

/**
 * Factory interface for creating 3DS provider instances.
 * Each provider implementation should provide a factory that implements this interface.
 */
@Keep
interface ProviderFactory {
    
    /**
     * Create a new instance of the 3DS provider.
     * This should return a fresh, uninitialized provider instance.
     */
    @Keep
    fun createProvider(): ThreeDSProvider
    
    /**
     * Get the type of provider this factory creates.
     */
    @Keep
    fun getProviderType(): ThreeDSProviderType
    
    /**
     * Get the version of the provider implementation.
     */
    @Keep
    fun getProviderVersion(): String
    
    /**
     * Check if this provider is available in the current environment.
     * This should verify that all required dependencies are present.
     */
    @Keep
    fun isAvailable(): Boolean
    
    /**
     * Get the priority of this provider for automatic selection.
     * Higher values indicate higher priority.
     * Default providers should use values 0-100, custom providers can use higher values.
     */
    @Keep
    fun getPriority(): Int
    
    /**
     * Check if this provider supports the given configuration.
     * Used for provider selection and validation.
     */
    @Keep
    fun supportsConfiguration(configuration: ThreeDSConfiguration): Boolean
    
    /**
     * Get a list of supported features by this provider.
     * This can be used for feature detection and provider comparison.
     */
    @Keep
    fun getSupportedFeatures(): List<String>
}
