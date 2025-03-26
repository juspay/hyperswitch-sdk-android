package io.hyperswitch.react

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
import `in`.juspay.hyperotareact.HyperOTAServicesReact
import io.hyperswitch.BuildConfig
import io.hyperswitch.R
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager

import io.hyperswitch.hyperota.HyperOtaLogger

open class MainApplication : Application(), ReactApplication {
    private  var hyperOTAServices : HyperOTAServicesReact? = null
    private lateinit var tracker: HyperOtaLogger
    private lateinit var context : Context
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
                val hyperOTAUrl =  context.getString(R.string.hyperOTAEndPoint)
                if (hyperOTAUrl != "hyperOTA_END_POINT_" ) {
                    tracker = HyperOtaLogger()
                    hyperOTAServices = HyperOTAServicesReact(
                        context.applicationContext,
                        "hyperswitch",
                        "hyperswitch.bundle",
                        BuildConfig.VERSION_NAME,
                        "${hyperOTAUrl}/mobile-ota/android/${BuildConfig.VERSION_NAME}/config.json",
                        tracker,
                    )
                }
                if (hyperOTAServices?.getBundlePath()?.contains("ios") == true){
                    return "assets://hyperswitch.bundle"
                }else {
                    return hyperOTAServices?.getBundlePath() ?: "assets://hyperswitch.bundle"
                }
            }
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        this.context = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
             override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
             override fun onActivityStarted(activity: Activity) {}
             override fun onActivityStopped(activity: Activity) {}
             override fun onActivityResumed(activity: Activity) {}
             override fun onActivityPaused(activity: Activity) {}
             override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
             override fun onActivityDestroyed(activity: Activity) {
                     val fileManager = LogFileManager(context)
                     fileManager.addLog(HyperLogManager.getAllLogsAsString())
             }
        })
        SoLoader.init(this, false)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            load()
        }
    }
}