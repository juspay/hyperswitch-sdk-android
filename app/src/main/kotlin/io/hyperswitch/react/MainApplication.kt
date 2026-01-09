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
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import io.hyperswitch.BuildConfig
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.R
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.SDKEnvironment

open class MainApplication : Application(), ReactApplication {
    private lateinit var context: Context

    override val reactNativeHost: ReactNativeHost = object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> = PackageList(this).packages.apply {
            // Packages that cannot be autolinked yet can be added manually here, for example:
            // add(MyReactNativePackage())
            add(HyperPackage())
        }

        override fun getJSMainModuleName(): String = "index"

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED

        override fun getJSBundleFile(): String {
            try {
                val environment = getEnvironment(PaymentConfiguration.publishableKey())
                val hyperOTAUrl = context.getString(
                    if (environment == SDKEnvironment.SANDBOX)
                        R.string.hyperOTASandBoxEndPoint
                    else
                        R.string.hyperOTAEndPoint
                )
                if (hyperOTAUrl != "hyperOTA_END_POINT_") {
                    val hyperOTAClass = Class.forName("io.hyperswitch.airborne.HyperOTA")

                    val constructor = hyperOTAClass.getConstructor(
                        Context::class.java,
                        String::class.java,
                        String::class.java
                    )

                    val instance = constructor.newInstance(
                        context.applicationContext,   // Context ✔
                        BuildConfig.VERSION_NAME,
                        hyperOTAUrl
                    )

                    val getBundlePath = hyperOTAClass.getMethod("getBundlePath")
                    val assetsPath = getBundlePath.invoke(instance) as String
                    return assetsPath
                }
                return "assets://hyperswitch.bundle"
            } catch (e: Exception) {
                return "assets://hyperswitch.bundle"
            }
        }
    }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        this.context = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context, BuildConfig.VERSION_NAME))
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
        SoLoader.init(this, OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            load()
        }
    }
}