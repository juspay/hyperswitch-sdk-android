package io.hyperswitch.demoapp

import android.app.Application
import android.util.Log
import io.hyperswitch.HyperswitchSDK

/**
 * Test Application class that simulates a merchant's own Application
 * WITHOUT extending io.hyperswitch.react.MainApplication
 * 
 * This demonstrates the issue where merchants want to use their own
 * Application class for security and control reasons.
 * 
 * Expected behavior BEFORE fix: Runtime crash "Application is not a ReactApplication"
 * Expected behavior AFTER fix: Works perfectly with HyperSDK.initialize()
 */
class TestApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d("TestApplication", "==========================================")
        Log.d("TestApplication", "TestApplication.onCreate() called")
        Log.d("TestApplication", "==========================================")
        Log.d("TestApplication", "Merchant's own initialization complete")

        try {
            HyperswitchSDK.initialize(this)
            Log.d("TestApplication", "HyperSDK initialized successfully")
        } catch (e: Exception) {
            Log.e("TestApplication", "Failed to initialize HyperSDK", e)
        }
    }
    

}
