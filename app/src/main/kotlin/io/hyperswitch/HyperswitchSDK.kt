package io.hyperswitch

import android.app.Application
import android.content.Context
import com.facebook.react.PackageList
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.SDKEnvironment
import io.hyperswitch.react.HyperPackage

object HyperswitchSDK {
    private var reactNativeHost: ReactNativeHost? = null
    private var reactHost: ReactHost? = null
    private var isInitialized = false
    fun initialize(application: Application) {
        if (isInitialized) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(application, BuildConfig.VERSION_NAME)
            )
        SoLoader.init(application, OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            load()
        }

        reactNativeHost = createReactNativeHost(application)
        reactHost = getDefaultReactHost(application.applicationContext, reactNativeHost!!)
        isInitialized = true
    }

    private fun getBundleFromAirborne(application: Application, defaultAssetPath: String): String {
        try {
            val environment = getEnvironment(PaymentConfiguration.publishableKey())
            val hyperOTAUrl = application.getString(
                if (environment == SDKEnvironment.SANDBOX) R.string.hyperOTASandBoxEndPoint
                else R.string.hyperOTAEndPoint
            )
            if (hyperOTAUrl != "hyperOTA_END_POINT_") {
                val hyperOTAClass = Class.forName("io.hyperswitch.airborne.AirborneOTA")
                val constructor = hyperOTAClass.getConstructor(
                    Context::class.java, String::class.java, String::class.java
                )
                val instance = constructor.newInstance(
                    application.applicationContext, BuildConfig.VERSION_NAME, hyperOTAUrl
                )
                val getBundlePath = hyperOTAClass.getMethod("getBundlePath")
                val assetsPath = getBundlePath.invoke(instance) as String
                return assetsPath
            }
            return defaultAssetPath
        } catch (_: Exception) {
            return defaultAssetPath
        }
    }

    private fun createReactNativeHost(
        application: Application,
    ): ReactNativeHost {
        return object : DefaultReactNativeHost(application) {
            override fun getPackages(): List<ReactPackage> {
                return PackageList(this).packages.apply {
                    add(HyperPackage())
                }
            }
            override fun getJSMainModuleName(): String = "index"
            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
            override fun getJSBundleFile(): String =
                getBundleFromAirborne(application, "assets://hyperswitch.bundle")
        }
    }

    internal fun getReactNativeHost(): ReactNativeHost {
        val host = checkNotNull(reactNativeHost) {
            "HyperswitchSDK not initialized. Call HyperswitchSDK.initialize() in Application.onCreate()"
        }
        return host
    }
    internal fun getReactHost(): ReactHost? {
        check(isInitialized) {
            "HyperswitchSDK not initialized. Call HyperswitchSDK.initialize() in Application.onCreate()"
        }
        return reactHost
    }
}
