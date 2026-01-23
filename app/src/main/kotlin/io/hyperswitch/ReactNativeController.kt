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
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.SDKEnvironment
import io.hyperswitch.react.HyperPackage

/**
 * ReactNativeController
 *
 * Entry point for initializing and accessing the Hyperswitch React Native runtime.
 * This object is responsible for:
 * - Initializing React Native (Old & New Architecture)
 * - Loading JS bundles (OTA or bundled assets)
 * - Managing ReactHost / ReactNativeHost lifecycle
 * - Setting up crash handling and native dependencies
 *
 * This SDK is designed to be initialized once per application lifecycle.
 */
object ReactNativeController {

    private var reactNativeHost: ReactNativeHost? = null
    private var reactHost: ReactHost? = null
    @Volatile
    private var isInitialized = false

    /**
     * Resolves the JavaScript bundle path using Hyper Airborne OTA if available.
     *
     * Behavior:
     * - Determines SDK environment using the publishable key
     * - Reads OTA endpoint from resources based on environment
     * - Dynamically loads AirborneOTA via reflection (optional dependency)
     * - Fetches the OTA-downloaded bundle path
     * - Falls back to bundled assets if OTA is disabled, unavailable, or fails
     *
     * @param application Application context
     * @return Path to the JS bundle (OTA or bundled asset)
     */
    private fun getBundleFromAirborne(application: Application): String {
        try {
            val environment = getEnvironment(PaymentConfiguration.publishableKey())
            val hyperOTAUrl = application.getString(
                if (environment == SDKEnvironment.SANDBOX)
                    R.string.hyperOTASandBoxEndPoint
                else
                    R.string.hyperOTAEndPoint
            )

            // Ensure OTA endpoint is valid
            if (hyperOTAUrl != "hyperOTA_END_POINT_") {
                val hyperOTAClass =
                    Class.forName("io.hyperswitch.airborne.AirborneOTA")

                val constructor = hyperOTAClass.getConstructor(
                    Context::class.java,
                    String::class.java,
                    String::class.java
                )

                val instance = constructor.newInstance(
                    application.applicationContext,
                    BuildConfig.VERSION_NAME,
                    hyperOTAUrl
                )

                val getBundlePath =
                    hyperOTAClass.getMethod("getBundlePath")

                return getBundlePath.invoke(instance) as String
            }

            return "assets://hyperswitch.bundle"
        } catch (_: Exception) {
            // Any failure gracefully falls back to bundled assets
            return "assets://hyperswitch.bundle"
        }
    }

    /**
     * Creates and configures the ReactNativeHost instance.
     *
     * Responsibilities:
     * - Registers required React Native packages
     * - Enables Hermes and New Architecture flags
     * - Resolves JS bundle source (OTA or bundled)
     *
     * @param application Application context
     * @param enableOTA Whether OTA-based JS loading is enabled and only works when dependency is added
     * @return Configured ReactNativeHost instance
     */
    private fun createReactNativeHost(
        application: Application,
        enableOTA: Boolean
    ): ReactNativeHost {
        return object : DefaultReactNativeHost(application) {
            override fun getPackages(): List<ReactPackage> {
                return PackageList(this).packages.apply {
                    add(HyperPackage())
                }
            }
            override fun getJSMainModuleName(): String = "index"
            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
            override val isNewArchEnabled: Boolean =
                BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean =
                BuildConfig.IS_HERMES_ENABLED
            override fun getJSBundleFile(): String =
                if (enableOTA) {
                    getBundleFromAirborne(application)
                } else {
                    "assets://hyperswitch.bundle"
                }
        }
    }

    /**
     * Returns whether the SDK has already been initialized.
     *
     * @return true if initialized, false otherwise
     */
    fun getIsInitialized(): Boolean {
        return isInitialized
    }

    /**
     * Returns the initialized ReactNativeHost instance.
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return ReactNativeHost
     */
    fun getReactNativeHost(): ReactNativeHost {
        return checkNotNull(reactNativeHost) {
            "ReactNative not initialized. Call ReactNativeController.initialize()"
        }
    }

    /**
     * Returns the initialized ReactHost instance.
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return ReactHost
     */
    fun getReactHost(): ReactHost {
        return checkNotNull(reactHost) {
            "ReactNative not initialized. Call ReactNativeController.initialize()"
        }
    }

    /**
     * Initializes the Hyperswitch SDK.
     *
     * This method:
     * - Ensures single initialization (thread-safe)
     * - Registers a global crash handler
     * - Initializes SoLoader
     * - Loads New Architecture entry point if enabled
     * - Creates ReactNativeHost and ReactHost instances
     *
     * OTA behavior depends on the `enableOTA` flag and
     * the availability of the Airborne dependency.
     *
     * @param application Application instance
     * @param enableOTA Enables OTA-based JS bundle loading
     */
    fun initialize(application: Application, enableOTA: Boolean) {
        try {
            synchronized(this) {
                if (isInitialized) return

                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(application, BuildConfig.VERSION_NAME)
                )

                SoLoader.init(application, OpenSourceMergedSoMapping)

                if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
                    load()
                }

                reactNativeHost =
                    createReactNativeHost(application, enableOTA)

                reactHost = getDefaultReactHost(
                    application.applicationContext,
                    reactNativeHost!!
                )

                isInitialized = true
            }
        } catch (e: Exception) {
            HyperLogManager.addLog(
                HSLog.LogBuilder()
                    .value("Failed to initialize Hyperswitch SDK: ${e.message}")
                    .category(LogCategory.API)
                    .logType("error")
                    .build()
            )
        }
    }

    /**
     * Initializes the SDK with OTA enabled by default.
     *
     * Equivalent to calling:
     * initialize(application, true)
     *
     * @param application Application instance
     */
    fun initialize(application: Application) {
        initialize(application, true)
    }
}
