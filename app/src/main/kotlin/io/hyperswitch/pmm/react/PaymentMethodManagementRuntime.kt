package io.hyperswitch.pmm.react

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.ReactHost
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import io.hyperswitch.react.HostHealth
import io.hyperswitch.react.PackageList
import io.hyperswitch.react.createHyperReactHost
import io.hyperswitch.react.ReactNativeController
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * The Payment Method Management React host. A separate realm from the payments host: its
 * own bundle, its own native module, and none of the payments modules. Every PMM sheet or
 * widget is one React root on it, told apart by root tag.
 */
internal class PaymentMethodManagementRuntime private constructor(application: Application) :
    DefaultLifecycleObserver {

    private val moduleRef = AtomicReference<WeakReference<HyperPMMModule>?>(null)

    /** Whether this host could start; see [HostHealth]. */
    val health = HostHealth("payment method management")

    val reactHost: ReactHost = createReactHost(application)

    fun attach(module: HyperPMMModule) = moduleRef.set(WeakReference(module))

    fun detach() = moduleRef.set(null)

    /**
     * Reaches the bundle only once it has subscribed, which it does as it mounts. A
     * merchant-driven confirm therefore always lands: the widget is on screen by then.
     */
    fun emitTriggerWidgetAction(payload: WritableMap): Boolean {
        val module = moduleRef.get()?.get() ?: return false
        return try {
            module.emitWidgetAction(payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Boots the host, and with it the bundle, ahead of the first PMM surface. Idempotent. */
    fun warmUp() {
        try {
            if (health.initFailure == null) reactHost.start()
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

    /** Built on the shared host construction, over this host's entry file, bundle and package. */
    private fun createReactHost(application: Application): ReactHost {
        ReactNativeController.initialize(application)
        return createHyperReactHost(
            application,
            entryFile = ENTRY_FILE,
            bundlePath = "assets://$BUNDLE_NAME",
            packages = PackageList(application).packages.apply { add(PaymentMethodManagementPackage(this@PaymentMethodManagementRuntime)) },
            health = health,
        )
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }

    companion object {
        const val ENTRY_FILE = "index.payment-method-management"
        const val BUNDLE_NAME = "hyperswitch-payment-method-management.bundle"

        @Volatile
        private var instance: PaymentMethodManagementRuntime? = null

        /** Created on first use and kept for the life of the process. */
        fun get(application: Application): PaymentMethodManagementRuntime =
            instance ?: synchronized(this) {
                instance ?: PaymentMethodManagementRuntime(application).also { instance = it }
            }
    }
}
