package io.hyperswitch.paymentmethods

import android.app.Application
import android.content.Context
import android.util.Log
import com.facebook.react.ReactHost
import com.facebook.react.bridge.JSBundleLoader
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.defaults.DefaultComponentsRegistry
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHostDelegate
import com.facebook.react.fabric.ComponentFactory
import com.facebook.react.runtime.ReactHostImpl
import com.facebook.react.defaults.DefaultTurboModuleManagerDelegate
import com.facebook.react.runtime.hermes.HermesInstance
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.DisplayMetricsHolder
import com.facebook.soloader.SoLoader
import io.hyperswitch.paymentsession.PaymentSessionRouter
import io.hyperswitch.react.HyperEventEmitter
import io.hyperswitch.react.HyperPackage
import io.hyperswitch.react.PackageList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the React Native runtime for a single [PaymentMethodSession].
 *
 * Every instance creates a **fresh** [ReactHost] via [ReactHostImpl] directly — deliberately
 * bypassing `DefaultReactHost.getDefaultReactHost()` (which statically caches the first host).
 * This guarantees every payment-method session runs on its own RN host, isolated from the main
 * payment SDK host and from other sessions.
 */
internal class PaymentMethodSessionReactHostProvider(
    private val application: Application,
) {

    /** Event emitter scoped to this session's host. */
    val eventEmitter = HyperEventEmitter()

    /** Session router scoped to this session's host. */
    val sessionRouter = PaymentSessionRouter()

    val reactHost: ReactHost by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createReactHost()
    }

    @OptIn(UnstableReactNativeAPI::class)
    private fun createReactHost(): ReactHost {
        ensureRuntimeReady(application)

        val packages = PackageList(application).packages.apply {
            add(HyperPackage(eventEmitter, sessionRouter))
        }

        val delegate = DefaultReactHostDelegate(
            jsMainModulePath = JS_MAIN_MODULE_PATH,
            jsBundleLoader = JSBundleLoader.createAssetLoader(
                application,
                resolveBundlePath(application),
                true,
            ),
            reactPackages = packages,
            jsRuntimeFactory = HermesInstance(),
            turboModuleManagerDelegateBuilder = DefaultTurboModuleManagerDelegate.Builder(),
            exceptionHandler = { e ->
                Log.e(TAG, "PaymentMethodSession React host exception: ${e.message}")
            },
        )

        val componentFactory = ComponentFactory()
        DefaultComponentsRegistry.register(componentFactory)

        return ReactHostImpl(
            application,
            delegate,
            componentFactory,
            false /* allowPackagerServerAccess */,
            io.hyperswitch.paymentmethods.BuildConfig.DEBUG,
        )
    }

    companion object {
        private const val TAG = "PMSessionReactHost"
        private const val JS_MAIN_MODULE_PATH = "index"

        private val runtimeReady = AtomicBoolean(false)

        /**
         * One-time, process-wide native runtime setup (SoLoader / display metrics /
         * new-arch entry point). Safe to race with the main SDK's ReactNativeController —
         * every step is guarded and failure-tolerant.
         */
        private fun ensureRuntimeReady(application: Application) {
            if (!runtimeReady.compareAndSet(false, true)) return
            try {
                SoLoader.init(application, OpenSourceMergedSoMapping)
            } catch (_: Throwable) {
            }
            try {
                DisplayMetricsHolder.initDisplayMetricsIfNotInitialized(application.applicationContext)
            } catch (_: Throwable) {
            }
            try {
                DefaultNewArchitectureEntryPoint.load()
            } catch (_: Throwable) {
            }
        }

        /**
         * Resolves the JS bundle path the same way the main SDK does — Hyper Airborne OTA
         * when available, falling back to the bundled asset.
         */
        private fun resolveBundlePath(application: Application): String {
            try {
                val airborneUrl = application.getString(io.hyperswitch.R.string.hyperOTAEndPoint)
                if (airborneUrl != "hyperOTA_END_POINT_") {
                    val airborneClass = Class.forName("io.hyperswitch.airborne.AirborneOTA")
                    val constructor = airborneClass.getConstructor(
                        Context::class.java,
                        String::class.java,
                        String::class.java,
                    )
                    val instance = constructor.newInstance(
                        application.applicationContext,
                        io.hyperswitch.BuildConfig.VERSION_NAME,
                        airborneUrl,
                    )
                    return airborneClass.getMethod("getBundlePath").invoke(instance) as String
                }
            } catch (_: Exception) {
            }
            return "assets://hyperswitch.bundle"
        }
    }
}
