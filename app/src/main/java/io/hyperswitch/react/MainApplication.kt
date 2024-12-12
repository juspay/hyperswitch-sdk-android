package io.hyperswitch.react

import android.app.Application
import android.util.Log
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
// import com.microsoft.codepush.react.CodePush
import io.hyperswitch.BuildConfig
import `in`.juspay.services.HyperOTAServices
open class MainApplication : Application(), ReactApplication {
    lateinit var hyperOTAServices : HyperOTAServices
    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> =
                PackageList(this).packages.apply {
                    // Packages that cannot be autolinked yet can be added manually here, for example:
                    // add(MyReactNativePackage())
                    add(HyperPackage())
                }

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED

            override fun getJSBundleFile(): String {
                // CodePush.overrideAppVersion(BuildConfig.VERSION_NAME)
                // return CodePush.getJSBundleFile("hyperswitch.bundle")
                return hyperOTAServices.getBundlePath()
            }
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        // CodePush.setReactInstanceHolder { reactNativeHost.reactInstanceManager }
        Log.i("called", "calledOTAServices")
        this.hyperOTAServices = HyperOTAServices(this.applicationContext, "hyperswitch",
            "index.android.bundle","http://10.0.2.2:3000/files%s/%s/%s-config.json?toss=%s")
        super.onCreate()
        SoLoader.init(this, false)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            load()
        }
    }
}