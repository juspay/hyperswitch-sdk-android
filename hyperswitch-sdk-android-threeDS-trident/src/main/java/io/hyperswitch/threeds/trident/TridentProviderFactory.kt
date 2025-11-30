package io.hyperswitch.threeds.trident

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ThreeDSConfiguration
import io.hyperswitch.threeds.models.ThreeDSProviderType
import io.hyperswitch.threeds.provider.ProviderFactory
import io.hyperswitch.threeds.provider.ThreeDSProvider

/**
 * Factory for creating Trident ThreeDSProvider instances.
 * This factory is automatically discovered and registered by the provider registry.
 */
@Keep
class TridentProviderFactory : ProviderFactory {
    
    override fun getProviderType(): ThreeDSProviderType = ThreeDSProviderType.TRIDENT
    
    override fun getProviderVersion(): String = "1.0.8-rc.04"
    
    override fun getSupportedFeatures(): List<String> {
        return listOf("3DS2", "frictionless", "challenge", "app-based")
    }
    
    override fun createProvider(): ThreeDSProvider {
        return TridentThreeDSProvider()
    }
    
    override fun isAvailable(): Boolean {
        return try {
            // Check if Trident SDK classes are available
            Class.forName("in.juspay.trident.core.ThreeDS2Service")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    override fun supportsConfiguration(configuration: ThreeDSConfiguration): Boolean {
        // Check if this factory can create a provider that supports the given configuration
        return isAvailable() && TridentThreeDSProvider().supportsConfiguration(configuration)
    }
    
    override fun getPriority(): Int {
        // Higher priority than mock provider, but can be adjusted based on preference
        return 100
    }
}
