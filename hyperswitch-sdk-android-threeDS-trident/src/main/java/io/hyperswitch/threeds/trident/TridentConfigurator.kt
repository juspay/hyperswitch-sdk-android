package io.hyperswitch.threeds.trident

import `in`.juspay.trident.core.ConfigParameters

/**
 * Configurator class for Trident SDK configuration parameters.
 * Similar to HsTridentConfigurator from the example.
 */
object TridentConfigurator {
    
    lateinit var configParams: ConfigParameters
        private set
    
    /**
     * Set up configuration parameters for Trident SDK.
     * This should be called before initializing the SDK.
     */
    fun setConfigParameters() {
        configParams = ConfigParameters()
    }
    
    /**
     * Check if configuration parameters have been set.
     */
    fun isConfigured(): Boolean {
        return ::configParams.isInitialized
    }
}
