package io.hyperswitch.paymentmethods

import android.app.Application
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the React Native runtime for a single [PaymentMethodSession].
 *
 * Every `PaymentMethodSession` constructs its own provider instance, and every provider
 * builds a **fresh** [ReactHost] via [ReactHostImpl] directly — the host is never created
 * through `DefaultReactHost.getDefaultReactHost()`, which statically caches the first host
 * and would return the same instance for every caller. [ReactHostImpl] itself holds no
 * static state, so N sessions in one process yield N fully independent runtimes (own
 * Hermes runtime, own JS thread, own [ComponentFactory], own [HyperEventEmitter]/
 * [PaymentSessionRouter] TurboModule wiring).
 */
internal class PaymentMethodSessionReactHostProvider(
    private val application: Application,
) {

    /** Monotonic id identifying this provider's host — distinct for every session. */
    val hostInstanceId: Int = hostCounter.incrementAndGet()

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

        val host = ReactHostImpl(
            application,
            delegate,
            componentFactory,
            false /* allowPackagerServerAccess */,
            io.hyperswitch.paymentmethods.BuildConfig.DEBUG,
        )
        Log.i(
            TAG,
            "Created dedicated React host instance #$hostInstanceId " +
                    "(@${Integer.toHexString(System.identityHashCode(host))}) for this payment-method session",
        )
        return host
    }

    companion object {
        private const val TAG = "PMSessionReactHost"
        private const val JS_MAIN_MODULE_PATH = "index"

        /** Dedicated bundle for payment-method session hosts — never the main bundle. */
        private const val PAYMENT_METHODS_BUNDLE_ASSET = "hyperswitch-payment-methods.bundle"
        private const val MAIN_BUNDLE_ASSET = "hyperswitch.bundle"

        private val runtimeReady = AtomicBoolean(false)

        /** Process-wide counter — every session's provider gets a fresh, unique id. */
        private val hostCounter = AtomicInteger(0)

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
         * Resolves the JS bundle path for this session's dedicated host:
         * the separate [PAYMENT_METHODS_BUNDLE_ASSET] asset shipped by this library.
         * Falls back to the main SDK bundle when the dedicated asset has not been
         * generated/packaged yet, so integrations degrade gracefully.
         */
        private fun resolveBundlePath(application: Application): String {
            val hasPaymentMethodsBundle = runCatching {
                application.assets.list("")?.contains(PAYMENT_METHODS_BUNDLE_ASSET) == true
            }.getOrDefault(false)

            return if (hasPaymentMethodsBundle) {
                "assets://$PAYMENT_METHODS_BUNDLE_ASSET"
            } else {
                Log.w(
                    TAG,
                    "$PAYMENT_METHODS_BUNDLE_ASSET not found in app assets — " +
                            "run `yarn bundle:android:payment-methods`; falling back to $MAIN_BUNDLE_ASSET",
                )
                "assets://$MAIN_BUNDLE_ASSET"
            }
        }
    }
}
