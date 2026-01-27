package io.hyperswitch.demoapp

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import io.hyperswitch.ReactNativeController

/**
 * DemoApplication
 * 
 * Custom Application class that implements ReactApplication for Detox E2E testing support.
 * Detox requires the Application to implement ReactApplication to synchronize with React Native.
 * 
 * This class delegates ReactNativeHost to the SDK's ReactNativeController singleton,
 * ensuring proper integration with the Hyperswitch SDK architecture.
 */
class DemoApplication : Application(), ReactApplication {

    override fun onCreate() {
        super.onCreate()
        // Initialize ReactNativeController to ensure ReactNativeHost is available
        ReactNativeController.initialize(this)
    }

    override val reactNativeHost: ReactNativeHost
        get() = ReactNativeController.getReactNativeHost()
}