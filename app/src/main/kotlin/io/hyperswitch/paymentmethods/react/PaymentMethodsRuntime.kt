package io.hyperswitch.paymentmethods.react

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.ReactHost
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.defaults.DefaultComponentsRegistry
import com.facebook.react.defaults.DefaultReactHostDelegate
import com.facebook.react.defaults.DefaultTurboModuleManagerDelegate
import com.facebook.react.fabric.ComponentFactory
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.runtime.ReactHostImpl
import com.facebook.react.runtime.hermes.HermesInstance
import io.hyperswitch.BuildConfig
import io.hyperswitch.react.HyperBundleLoader
import io.hyperswitch.react.HyperDevSupportManagerFactory
import io.hyperswitch.react.PackageList
import io.hyperswitch.react.ReactNativeController
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * The Payment Methods SDK's React host. A separate realm from the payments host: its own
 * bundle, its own native module, and none of the payments modules. Every form and every
 * field is one React root on it, told apart by root tag.
 */
internal class PaymentMethodsRuntime private constructor(application: Application) :
    DefaultLifecycleObserver {

    private val moduleRef = AtomicReference<WeakReference<PaymentMethodsModule>?>(null)

    val reactHost: ReactHost = createReactHost(application)

    fun attach(module: PaymentMethodsModule) = moduleRef.set(WeakReference(module))

    fun detach() = moduleRef.set(null)

    /**
     * Reaches the bundle only once it has subscribed, which it does as it loads. A form
     * therefore holds its commands until its own root has been heard from.
     */
    fun send(command: WritableMap): Boolean {
        val module = moduleRef.get()?.get() ?: return false
        return try {
            module.sendCommand(command)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Boots the host, and with it the bundle, ahead of the first form. Idempotent. */
    fun warmUp() {
        try {
            reactHost.start()
        } catch (_: Exception) {
        }
    }

    /**
     * React Native runs JS timers, and so every `fetch`, only while the host is resumed. The
     * host follows [activity] the way the payments host does; see HyperReactRuntime.follow.
     */
    fun follow(activity: Activity) {
        onMain {
            if (activity is LifecycleOwner) {
                activity.lifecycle.addObserver(this)
            } else {
                resume(activity)
                activity.application.registerActivityLifecycleCallbacks(PlainActivityWatcher(activity))
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) = resume(owner as Activity)

    override fun onPause(owner: LifecycleOwner) = reactHost.onHostPause()

    override fun onDestroy(owner: LifecycleOwner) {
        reactHost.onHostDestroy(owner as Activity)
        owner.lifecycle.removeObserver(this)
    }

    private fun resume(activity: Activity) =
        reactHost.onHostResume(activity, activity as? DefaultHardwareBackBtnHandler)

    private inner class PlainActivityWatcher(private val activity: Activity) :
        Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity === this.activity) resume(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity === this.activity) reactHost.onHostPause()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity !== this.activity) return
            reactHost.onHostDestroy(activity)
            activity.application.unregisterActivityLifecycleCallbacks(this)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }

    /** Same construction as the payments host, over this SDK's entry file, bundle and package. */
    @OptIn(UnstableReactNativeAPI::class)
    private fun createReactHost(application: Application): ReactHost {
        ReactNativeController.initialize(application)

        val delegate = DefaultReactHostDelegate(
            jsMainModulePath = ENTRY_FILE,
            jsBundleLoader = HyperBundleLoader.create(application, "assets://$BUNDLE_NAME", loadSynchronously = true),
            reactPackages = PackageList(application).packages.apply { add(PaymentMethodsPackage(this@PaymentMethodsRuntime)) },
            jsRuntimeFactory = HermesInstance(),
            turboModuleManagerDelegateBuilder = DefaultTurboModuleManagerDelegate.Builder(),
        )

        val componentFactory = ComponentFactory()
        DefaultComponentsRegistry.register(componentFactory)

        return ReactHostImpl(
            context = application,
            reactHostDelegate = delegate,
            componentFactory = componentFactory,
            allowPackagerServerAccess = true,
            useDevSupport = BuildConfig.DEBUG,
            // A dev bundle file of its own: the hosts share one process.
            devSupportManagerFactory = HyperDevSupportManagerFactory.forBuild(BuildConfig.DEBUG),
        )
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }

    companion object {
        const val ENTRY_FILE = "index.payment-methods"
        const val BUNDLE_NAME = "hyperswitch-payment-methods.bundle"

        @Volatile
        private var instance: PaymentMethodsRuntime? = null

        /** Created on first use and kept for the life of the process. */
        fun get(application: Application): PaymentMethodsRuntime =
            instance ?: synchronized(this) {
                instance ?: PaymentMethodsRuntime(application).also { instance = it }
            }
    }
}
