package io.hyperswitch.react

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import `in`.juspay.hyperotareact.HyperOTAServicesReact
import io.hyperswitch.BuildConfig
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
//import io.hyperswitch.logs.SomeThirdPartySDK

import io.hyperswitch.hyperota.HyperOtaLogger

open class MainApplication : Application(), ReactApplication {
    private lateinit var hyperOTAServices : HyperOTAServicesReact
    private lateinit var tracker: HyperOtaLogger
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
//                CodePush.overrideAppVersion(BuildConfig.VERSION_NAME)
//                return CodePush.getJSBundleFile("hyperswitch.bundle")
                return hyperOTAServices.getBundlePath()
            }
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        val context = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
    //  CodePush.setReactInstanceHolder { reactNativeHost.reactInstanceManager }
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                val fileManager = LogFileManager(context)
                fileManager.addLog(HyperLogManager.getAllLogsAsString())
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        this.tracker = HyperOtaLogger()
        SoLoader.init(this, false)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            load()
        }
        // Mocking a 3rd party sdk for testing different scenarios
        // val mockSDKInstance=SomeThirdPartySDK()
        // mockSDKInstance.mockFunction()
        this.hyperOTAServices = HyperOTAServicesReact(this.applicationContext,
            "hyperswitch",
            "hyperswitch.bundle",
            BuildConfig.VERSION_NAME,
            "https://d24vi5wq9ko537.cloudfront.net/hyperswitch/ota/android/${BuildConfig.VERSION_NAME}/config.json",
            this.tracker,
        )
    }
}
