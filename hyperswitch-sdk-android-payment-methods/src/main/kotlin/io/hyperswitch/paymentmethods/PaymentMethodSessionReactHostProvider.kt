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
import io.hyperswitch.react.HyperPackage
import io.hyperswitch.react.HyperReactRuntime
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
 * Hermes runtime, own JS thread, own [ComponentFactory], own
 * [HyperReactRuntime.sessionRouter] TurboModule wiring).
 */
internal class PaymentMethodSessionReactHostProvider(
    private val application: Application,
) {

    /** Monotonic id identifying this provider's host — distinct for every session. */
    val hostInstanceId: Int = hostCounter.incrementAndGet()

    /**
     * Backs the TurboModules registered on this session's host via [HyperPackage].
     * Its own [HyperReactRuntime.reactHost] is never read — this session builds and
     * owns a separate [ReactHostImpl] below (dedicated bundle, own lifecycle).
     */
    private val runtime = HyperReactRuntime(application)

    /** Emitter for this session's dedicated [PaymentMethodModule] — see [PaymentMethodPackage]. */
    internal val eventEmitter = PaymentMethodEventEmitter()

    val reactHost: ReactHost by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createReactHost()
    }

    @OptIn(UnstableReactNativeAPI::class)
    private fun createReactHost(): ReactHost {
        ensureRuntimeReady(application)

        val packages = PackageList(application).packages.apply {
            add(HyperPackage(runtime))
            add(PaymentMethodPackage(eventEmitter))
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
            BuildConfig.DEBUG /* allowPackagerServerAccess */,
            BuildConfig.DEBUG,
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

        /** Matches this bundle's actual entry file — see bundle:android:payment-methods
         * (--entry-file payment-methods.js). Must not be "index": that's the main app's
         * entry, and with allowPackagerServerAccess enabled, DevSupportManager uses this
         * path to fetch/refresh from Metro — the wrong path pulls in the main bundle
         * alongside this one, double-registering every shared native component. */
        private const val JS_MAIN_MODULE_PATH = "payment-methods"

        /** Dedicated bundle for payment-method session hosts — never the main bundle. */
        private const val PAYMENT_METHODS_BUNDLE_ASSET = "hyperswitch-payment-methods.bundle"

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
         * Resolves the JS bundle path for this session's dedicated host: always the
         * dedicated [PAYMENT_METHODS_BUNDLE_ASSET] asset shipped by this library.
         *
         * This never falls back to the main SDK bundle (`hyperswitch.bundle`) — that
         * bundle's `index.js` only registers `"hyperSwitch"`/`"HyperHeadless"`, never
         * the `"HyperswitchPaymentMethods"` component this session's surfaces need, so
         * loading it would silently produce a host that can never render a card form.
         * If the asset is genuinely missing, loading it anyway surfaces a clear
         * "asset not found" failure instead of that confusing dead end.
         */
        private fun resolveBundlePath(application: Application): String {
            val hasPaymentMethodsBundle = runCatching {
                application.assets.list("")?.contains(PAYMENT_METHODS_BUNDLE_ASSET) == true
            }.getOrDefault(false)

            if (!hasPaymentMethodsBundle) {
                Log.w(
                    TAG,
                    "$PAYMENT_METHODS_BUNDLE_ASSET not found in app assets — " +
                            "run `yarn bundle:android:payment-methods`",
                )
            }
            return "assets://$PAYMENT_METHODS_BUNDLE_ASSET"
        }
    }
}
