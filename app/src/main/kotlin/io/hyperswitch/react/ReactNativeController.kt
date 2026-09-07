package io.hyperswitch.react

import android.app.Application
import android.content.Context
import com.facebook.react.ReactHost
import com.facebook.react.bridge.JSBundleLoader
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.defaults.DefaultComponentsRegistry
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHostDelegate
import com.facebook.react.defaults.DefaultTurboModuleManagerDelegate
import com.facebook.react.fabric.ComponentFactory
import com.facebook.react.runtime.ReactHostImpl
import com.facebook.react.runtime.hermes.HermesInstance
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

/** Process-wide RN setup plus a per-session ReactHost factory. */
object ReactNativeController {

    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var application: Application? = null

    /** Host for entry points with no session: legacy flows, HyperActivity after process death. */
    val legacyRuntime: HyperReactRuntime by lazy {
        HyperReactRuntime(checkNotNull(application) {
            "ReactNativeController.initialize() must run before the legacy runtime is used"
        })
    }

    // One-shot handoff for HyperActivity (Intent-started); cleared on read.
    @Volatile
    private var pendingActivityRuntime: HyperReactRuntime? = null

    fun offerActivityRuntime(runtime: HyperReactRuntime) {
        pendingActivityRuntime = runtime
    }

    fun takeActivityRuntime(): HyperReactRuntime? =
        pendingActivityRuntime.also { pendingActivityRuntime = null }

    fun getIsInitialized(): Boolean = isInitialized.get()

    /** OTA bundle path if configured, else the bundled asset. */
    private fun getBundleFromAirborne(application: Application): String {
        try {
            val airborneUrl = application.getString(R.string.hyperOTAEndPoint)
            if (airborneUrl != "hyperOTA_END_POINT_") {
                val airborneClass = Class.forName("io.hyperswitch.airborne.AirborneOTA")
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
                val getBundlePath = airborneClass.getMethod("getBundlePath")
                return getBundlePath.invoke(instance) as String
            }
        } catch (_: Exception) {}
        return "assets://hyperswitch.bundle"
    }

    /** One-time, process-wide. Safe to call repeatedly. */
    fun initialize(application: Application) {
        try {
            synchronized(this) {
                if (isInitialized.get()) return
                this.application = application

                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(application, BuildConfig.VERSION_NAME)
                )
                SoLoader.init(application, OpenSourceMergedSoMapping)
                DisplayMetricsHolder.initDisplayMetricsIfNotInitialized(application.applicationContext)
                DefaultNewArchitectureEntryPoint.load()

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

    /** Same construction as DefaultReactHost.getDefaultReactHost, minus its process-wide memoization. */
    @OptIn(UnstableReactNativeAPI::class)
    internal fun createReactHost(application: Application, runtime: HyperReactRuntime): ReactHost {
        initialize(application)

        val bundlePath = getBundleFromAirborne(application)
        val bundleLoader = if (bundlePath.startsWith("assets://")) {
            JSBundleLoader.createAssetLoader(application, bundlePath, true)
        } else {
            JSBundleLoader.createFileLoader(bundlePath)
        }

        val delegate = DefaultReactHostDelegate(
            jsMainModulePath = "index",
            jsBundleLoader = bundleLoader,
            reactPackages = PackageList(application).packages.apply { add(HyperPackage(runtime)) },
            jsRuntimeFactory = HermesInstance(),
            turboModuleManagerDelegateBuilder = DefaultTurboModuleManagerDelegate.Builder(),
        )

        val componentFactory = ComponentFactory()
        DefaultComponentsRegistry.register(componentFactory)

        return ReactHostImpl(
            application,
            delegate,
            componentFactory,
            true, /* allowPackagerServerAccess */
            BuildConfig.DEBUG,
        )
    }
}
