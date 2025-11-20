package io.hyperswitch.threeds.provider

import android.util.Log
import androidx.annotation.Keep

/**
 * Registry for managing available 3DS providers.
 * Automatically discovers and registers available providers at runtime.
 */
@Keep
object ProviderRegistry {
    
    private const val TAG = "ProviderRegistry"
    
    private val registeredFactories = mutableListOf<ProviderFactory>()
    private var autoDiscoveryCompleted = false
    
    /**
     * List of known provider factory class names.
     * Add new provider factories here to enable auto-discovery.
     */
    private val knownProviderFactories = listOf(
        "io.hyperswitch.threeds.trident.TridentProviderFactory",
        "io.hyperswitch.threeds.netcetera.NetceteraProviderFactory",
        "io.hyperswitch.threeds_cardinal.CardinalProviderFactory",
    )
    
    /**
     * Automatically discover and register available providers.
     * This is called lazily on first access to ensure providers are ready.
     */
    @Keep
    private fun autoDiscoverProviders() {
        synchronized(registeredFactories) {
            if (autoDiscoveryCompleted) {
                return
            }
            
            Log.d(TAG, "Starting auto-discovery of 3DS providers...")
            
            for (factoryClassName in knownProviderFactories) {
                try {
                    // Try to load the factory class
                    val factoryClass = Class.forName(factoryClassName)
                    val factory = factoryClass.getDeclaredConstructor().newInstance() as ProviderFactory
                    
                    // Check if the provider is available (SDK dependencies present)
                    if (factory.isAvailable()) {
                        // Only register if not already registered
                        val existingFactory = registeredFactories.find { 
                            it.getProviderType() == factory.getProviderType() 
                        }
                        
                        if (existingFactory == null) {
                            registeredFactories.add(factory)
                            Log.d(TAG, "Auto-registered provider: ${factory.getProviderType().name} " +
                                    "v${factory.getProviderVersion()} (priority: ${factory.getPriority()})")
                        } else {
                            Log.d(TAG, "Provider ${factory.getProviderType().name} already registered, skipping")
                        }
                    } else {
                        Log.d(TAG, "Provider ${factory.getProviderType().name} not available (SDK not in classpath)")
                    }
                } catch (e: ClassNotFoundException) {
                    // Provider module not included in dependencies, skip silently
                    Log.d(TAG, "Provider factory not found: $factoryClassName (module not included)")
                } catch (e: Exception) {
                    // Log other errors but continue with other providers
                    Log.w(TAG, "Error loading provider factory $factoryClassName: ${e.message}")
                }
            }
            
            autoDiscoveryCompleted = true
            Log.d(TAG, "Auto-discovery completed. Registered ${registeredFactories.size} provider(s)")
        }
    }
    
    /**
     * Ensure auto-discovery has been performed.
     * Called internally before accessing providers.
     */
    @Keep
    private fun ensureAutoDiscovery() {
        if (!autoDiscoveryCompleted) {
            autoDiscoverProviders()
        }
    }
    
    /**
     * Register a provider factory manually.
     * This is used for testing and custom provider registration.
     * Manual registration takes precedence over auto-discovered providers.
     */
    @Keep
    fun registerProvider(factory: ProviderFactory) {
        synchronized(registeredFactories) {
            // Remove existing factory with same type
            registeredFactories.removeAll { it.getProviderType() == factory.getProviderType() }
            registeredFactories.add(factory)
            Log.d(TAG, "Manually registered provider: ${factory.getProviderType().name}")
        }
    }
    
    /**
     * Unregister a provider factory.
     */
    @Keep
    fun unregisterProvider(providerType: io.hyperswitch.threeds.models.ThreeDSProviderType) {
        synchronized(registeredFactories) {
            registeredFactories.removeAll { it.getProviderType() == providerType }
        }
    }
    
    /**
     * Get all available provider factories.
     * Automatically discovers providers if not already done.
     */
    @Keep
    fun getAvailableProviders(): List<ProviderFactory> {
        ensureAutoDiscovery()
        synchronized(registeredFactories) {
            return registeredFactories.toList()
        }
    }
    
    /**
     * Get a specific provider factory by type.
     * Automatically discovers providers if not already done.
     */
    @Keep
    fun getProvider(type: io.hyperswitch.threeds.models.ThreeDSProviderType): ProviderFactory? {
        ensureAutoDiscovery()
        synchronized(registeredFactories) {
            return registeredFactories.find { it.getProviderType() == type }
        }
    }
    
    /**
     * Clear all registered providers.
     * Useful for testing.
     */
    @Keep
    fun clearProviders() {
        synchronized(registeredFactories) {
            registeredFactories.clear()
        }
    }
    
    /**
     * Get providers sorted by priority (highest first).
     * Automatically discovers providers if not already done.
     */
    @Keep
    fun getProvidersByPriority(): List<ProviderFactory> {
        ensureAutoDiscovery()
        synchronized(registeredFactories) {
            return registeredFactories.sortedByDescending { it.getPriority() }
        }
    }
    
    /**
     * Force re-discovery of providers.
     * Useful for testing or when providers are added dynamically.
     */
    @Keep
    fun rediscoverProviders() {
        synchronized(registeredFactories) {
            autoDiscoveryCompleted = false
            registeredFactories.clear()
            autoDiscoverProviders()
        }
    }
}
