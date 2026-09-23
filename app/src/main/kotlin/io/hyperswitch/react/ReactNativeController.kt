package io.hyperswitch.react

import android.app.Application
import android.content.Context
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
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

    /** Whether the payments host could start; see [HostHealth]. */
    val health = HostHealth("payments")

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
            val host = runtime.reactHost
            if (health.initFailure == null) host.start()
        } catch (_: Exception) {}
    }

    /** Built on the shared host construction; called once, from [runtime]. */
    internal fun createReactHost(application: Application, runtime: HyperReactRuntime): ReactHost {
        initialize(application)
        // Evaluates the shared initial chunks before the entry bundle and tells the JS
        // side where on-demand chunks live (see HyperBundleLoader).
        return createHyperReactHost(
            application,
            entryFile = "index",
            bundlePath = getBundleFromAirborne(application),
            packages = PackageList(application).packages.apply { add(HyperPackage(runtime)) },
            health = health,
        )
    }
}
