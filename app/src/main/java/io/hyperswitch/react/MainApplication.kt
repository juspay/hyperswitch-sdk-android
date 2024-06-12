package io.hyperswitch.react

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
import com.microsoft.codepush.react.CodePush
import io.hyperswitch.BuildConfig

open class MainApplication : Application(), ReactApplication {

    // Initialize ReactNativeHost with DefaultReactNativeHost
    private val mReactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {

            // Override getPackages to include custom packages
            override fun getPackages(): List<ReactPackage> {
                // Get the list of auto-linked packages
                val packages = PackageList(this).packages
                // Add custom packages
                packages.add(HyperPackage())
                packages.add(HyperHeadlessPackage())
                return packages
            }

            // Specify the main module name
            override fun getJSMainModuleName(): String = "index"

            // Enable or disable developer support based on build type
            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            // Enable new architecture based on build config
            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED

            // Override method to get JS bundle file
            override fun getJSBundleFile(): String? {
                // Override the app version for CodePush
                CodePush.overrideAppVersion(BuildConfig.VERSION_NAME)
                // Return the JS bundle file from CodePush
                return CodePush.getJSBundleFile("hyperswitch.bundle")
            }
        }

    // Override method to get React Native Host
    override fun getReactNativeHost(): ReactNativeHost {
        return mReactNativeHost
    }

    // Override method to perform application initialization
    override fun onCreate() {
        // Set React instance holder for CodePush
        CodePush.setReactInstanceHolder { mReactNativeHost.reactInstanceManager }
        super.onCreate()
        // Initialize SoLoader
        SoLoader.init(this, false)
        // Check if new architecture is enabled and load native entry point
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            load()
        }
    }
}
