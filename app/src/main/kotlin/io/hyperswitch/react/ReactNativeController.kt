package io.hyperswitch.react

import android.app.Application
import android.content.Context
import com.facebook.react.ReactHost
import com.facebook.react.bridge.JSBundleLoader
import com.facebook.react.bridge.JSBundleLoaderDelegate
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

/** Process-wide React Native setup and the one shared runtime. */
object ReactNativeController {

    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var application: Application? = null

    /**
     * The shared runtime. One host renders every surface of every PaymentSession
     * and widget; it is created on first use and lives for the process.
     */
    val runtime: HyperReactRuntime by lazy {
        HyperReactRuntime(checkNotNull(application) {
            "ReactNativeController.initialize() must run before the React runtime is used"
        })
    }

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

    /**
     * Boots the shared host so the bundle is evaluated before the first session
     * needs it. Idempotent; a running host ignores it.
     */
    fun warmUp() {
        try {
            runtime.reactHost.start()
        } catch (_: Exception) {}
    }

    /** Same construction as DefaultReactHost.getDefaultReactHost; called once, from [runtime]. */
    @OptIn(UnstableReactNativeAPI::class)
    internal fun createReactHost(application: Application, runtime: HyperReactRuntime): ReactHost {
        initialize(application)

        // Resolved on the host's background thread: Airborne blocks until the OTA bundle is ready.
        val bundleLoader = object : JSBundleLoader() {
            override fun loadScript(delegate: JSBundleLoaderDelegate): String {
                val bundlePath = getBundleFromAirborne(application)
                val loader = if (bundlePath.startsWith("assets://")) {
                    JSBundleLoader.createAssetLoader(application, bundlePath, true)
                } else {
                    JSBundleLoader.createFileLoader(bundlePath)
                }
                return loader.loadScript(delegate)
            }
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
