package io.hyperswitch.react

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.ReactHost
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler

/**
 * The process-wide React runtime: one host (one JS realm, one bundle
 * evaluation) that renders every surface of every PaymentSession and widget.
 * Surfaces are told apart by root tag; the runtime keeps no per-session state.
 *
 * The constructor is public so SDK extension modules (e.g.
 * hyperswitch-sdk-android-payment-methods) can build a dedicated instance to back the
 * TurboModule wiring of their own hosts; only [ReactNativeController]'s instance ever
 * reads [reactHost].
 */
class HyperReactRuntime(application: Application) : DefaultLifecycleObserver {

    val eventEmitter = HyperEventEmitter()

    val reactHost: ReactHost by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReactNativeController.createReactHost(application, this)
    }

    /**
     * Makes the host follow [activity]'s lifecycle, the way a React Native app's own Activity
     * does. On Android, React Native runs JS timers only while the host is resumed, and every
     * `fetch` resolves through a timer, so a session's JS does no work until the Activity it
     * was created for has resumed the host and none while that Activity is paused. Destroying
     * the Activity drops the host's reference to it.
     *
     * Idempotent for a lifecycle owner (every AndroidX Activity): its lifecycle keeps one
     * registration per observer, and this runtime is the only observer the SDK adds. A plain
     * Activity has no lifecycle to observe, so it is taken as resumed now and the Application
     * reports its transitions from then on.
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

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}
