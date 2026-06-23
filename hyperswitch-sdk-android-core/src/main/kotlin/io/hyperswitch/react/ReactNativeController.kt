package io.hyperswitch.react

import android.app.Application
import android.content.Context
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import io.hyperswitch.core.BuildConfig as CoreBuildConfig
import io.hyperswitch.core.R
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
 * - Initializing React Native (Old & New Architecture)
 * - Loading JS bundles (OTA or bundled assets)
 * - Managing ReactHost / ReactNativeHost lifecycle
 * - Setting up crash handling and native dependencies
 *
 * This SDK is designed to be initialized once per application lifecycle.
 */
object ReactNativeController {

    @Volatile
    private var reactNativeHost = AtomicReference<ReactNativeHost?>(null)

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
                    CoreBuildConfig.VERSION_NAME,
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
     * Attempts to load the host-provided [HyperReactPackageProvider] implementation
     * via reflection. This lives in :app so it can reference the autolinked PackageList.
     */
    private fun loadPackageProvider(): HyperReactPackageProvider? {
        return try {
            val clazz = Class.forName("io.hyperswitch.HyperswitchReactPackageProvider")
            clazz.getDeclaredConstructor().newInstance() as HyperReactPackageProvider
        } catch (_: Exception) {
            null
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
     * @return Configured ReactNativeHost instance
     */
    private fun createReactNativeHost(
        application: Application,
    ): ReactNativeHost {
        return object : DefaultReactNativeHost(application) {
            override fun getPackages(): List<ReactPackage> {
                return loadPackageProvider()?.getPackages(this)
                    ?: listOf(HyperPackage())
            }
            override fun getJSMainModuleName(): String = "index"
            override fun getUseDeveloperSupport(): Boolean = CoreBuildConfig.DEBUG
            override val isNewArchEnabled: Boolean =
                HyperswitchBuildConfig.isNewArchitectureEnabled
            override val isHermesEnabled: Boolean =
                HyperswitchBuildConfig.isHermesEnabled
            override fun getJSBundleFile(): String =
                    getBundleFromAirborne(application)

        }
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
     * Returns the initialized ReactNativeHost instance.
     *
     * @throws IllegalStateException if SDK is not initialized
     * @return ReactNativeHost
     */
    fun getReactNativeHost(): ReactNativeHost {
        return checkNotNull(reactNativeHost.get()) {
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
     * - Creates ReactNativeHost and ReactHost instances
     * @param application Application instance
     */
    fun initialize(application: Application) {
        try {
            synchronized(this) {
                if (isInitialized.get()) return

                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(application, CoreBuildConfig.VERSION_NAME)
                )

                SoLoader.init(application, OpenSourceMergedSoMapping)

                if (HyperswitchBuildConfig.isNewArchitectureEnabled) {
                    DefaultNewArchitectureEntryPoint.load()
                }

                reactNativeHost.set(createReactNativeHost(application))

                reactHost.set(
                    DefaultReactHost.getDefaultReactHost(
                        application.applicationContext,
                        reactNativeHost.get()!!,
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