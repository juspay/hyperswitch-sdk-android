package io.hyperswitch.react

import android.app.Application
import android.content.Context
import io.hyperswitch.react.PackageList
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.DisplayMetricsHolder
import com.facebook.soloader.SoLoader
import io.hyperswitch.BuildConfig
import io.hyperswitch.R
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ReactNativeController
 *
 * Entry point for initializing and accessing the Hyperswitch React Native runtime.
 * This object is responsible for:
 * - Initializing React Native (New Architecture / bridgeless)
 * - Loading JS bundles (OTA or bundled assets)
 * - Managing the ReactHost lifecycle
 * - Setting up crash handling and native dependencies
 *
 * This SDK is designed to be initialized once per application lifecycle.
 */
object ReactNativeController {

    val eventEmitter = HyperEventEmitter()

    @Volatile
    private var reactHost = AtomicReference<ReactHost?>(null)

    @Volatile
    private var isInitialized = AtomicBoolean(false)

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
//            val environment = SDKEnvironment.PROD
            // TODO: change this to ENV check based on the Configuration.
            val airborneUrl = application.getString(
                R.string.hyperOTAEndPoint
            )

            // Ensure OTA endpoint is valid
            if (airborneUrl != "hyperOTA_END_POINT_") {
                val airborneClass =
                    Class.forName("io.hyperswitch.airborne.AirborneOTA")

                val constructor = airborneClass.getConstructor(
                    Context::class.java,
                    String::class.java,
                    String::class.java
                )

                val instance = constructor.newInstance(
                    application.applicationContext,
                    BuildConfig.VERSION_NAME,
                    airborneUrl
                )

                val getBundlePath =
                    airborneClass.getMethod("getBundlePath")
                return getBundlePath.invoke(instance) as String
            }
        } catch (_: Exception) {}
        return "assets://hyperswitch.bundle"
    }

    /**
     * Returns whether the SDK has already been initialized.
     *
     * @return true if initialized, false otherwise
     */
    fun getIsInitialized(): Boolean {
        return isInitialized.get()
    }

    /**
     * Returns the initialized ReactHost instance.
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return ReactHost
     */
    fun getReactHost(): ReactHost {
        return checkNotNull(reactHost.get()) {
            "ReactNative not initialized. Call ReactNativeController.initialize()"
        }
    }

    /**
     * Initializes the ReactNativeController.
     *
     * This method:
     * - Ensures single initialization (thread-safe)
     * - Registers a global crash handler
     * - Initializes SoLoader
     * - Loads New Architecture entry point if enabled
     * - Creates the ReactHost instance
     * @param application Application instance
     */
    fun initialize(application: Application) {
        try {
            synchronized(this) {
                if (isInitialized.get()) return

                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(application, BuildConfig.VERSION_NAME)
                )

                SoLoader.init(application, OpenSourceMergedSoMapping)

                DisplayMetricsHolder.initDisplayMetricsIfNotInitialized(application.applicationContext)

                DefaultNewArchitectureEntryPoint.load()

                reactHost.set(
                    DefaultReactHost.getDefaultReactHost(
                        context = application.applicationContext,
                        packageList = PackageList(application).packages.apply {
                            add(HyperPackage(eventEmitter))
                        },
                        jsMainModulePath = "index",
                        jsBundleFilePath = getBundleFromAirborne(application),
                        useDevSupport = BuildConfig.DEBUG,
                    )
                )

                isInitialized.set(true)
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
}