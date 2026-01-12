package io.hyperswitch.react

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

import io.hyperswitch.HyperswitchSDK

/**
* MainApplication - Legacy Application class for backward compatibility
*
* This class is maintained for backward compatibility with existing integrations
* that extend MainApplication. New integrations should use their own Application
* class and call HyperSDK.initialize() instead.
*
* Merchants who dont have their own Application class can continue to use this class
*
* Internally, this class now uses HyperSDK for all initialization, ensuring
* consistency between the two integration methods.
*/
open class MainApplication : Application(), ReactApplication {

   /**
    * ReactNativeHost is provided by HyperSDK singleton
    * This ensures consistency with the new initialization pattern
    */
   override val reactNativeHost: ReactNativeHost
       get() = HyperswitchSDK.getReactNativeHost()

   /**
    * ReactHost for new architecture, provided by HyperSDK singleton
    */
   override val reactHost: ReactHost
       get() = HyperswitchSDK.getReactHost()
           ?: getDefaultReactHost(applicationContext, reactNativeHost)

   override fun onCreate() {
       super.onCreate()
       HyperswitchSDK.initialize(this)
   }
}
