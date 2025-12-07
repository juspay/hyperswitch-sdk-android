package io.hyperswitch.webview.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * A [FrameLayout] container to hold the [HSWebView].
 * We need this to prevent WebView crash when the WebView is out of viewport and
 * [com.facebook.react.views.view.ReactViewGroup] clips the canvas.
 * The WebView will then create an empty offscreen surface and NPE.
 */
class HSWebViewWrapper(
    context: Context,
    webView: HSWebView,
) : FrameLayout(context), DefaultLifecycleObserver {
    
    private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var isDestroyed = false
    
    init {
        // We make the WebView as transparent on top of the container,
        // and let React Native sets background color for the container.
        webView.setBackgroundColor(Color.TRANSPARENT)
        addView(webView)
        
        // Register lifecycle observers for automatic cleanup
        setupLifecycleObservers()
    }

    val webView: HSWebView = getChildAt(0) as HSWebView
    
    private fun setupLifecycleObservers() {
        // Try to get lifecycle from Activity if it's a LifecycleOwner
        val activity = context as? Activity
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
        } else if (activity != null) {
            // Fallback: Use ActivityLifecycleCallbacks for non-lifecycle activities
            setupActivityLifecycleCallbacks(activity)
        }
        
        // Also observe ProcessLifecycle for app-level cleanup
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    private fun setupActivityLifecycleCallbacks(activity: Activity) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {
                if (a == activity) {
                    cleanupWebView()
                }
            }
        }
        
        activity.application?.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        cleanupWebView()
    }
    
    private fun cleanupWebView() {
        if (isDestroyed) {
            return
        }
        isDestroyed = true
        
        try {
            // Remove lifecycle observers
            val activity = context as? Activity
            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
            
            // Unregister activity lifecycle callbacks
            activityLifecycleCallbacks?.let {
                activity?.application?.unregisterActivityLifecycleCallbacks(it)
                activityLifecycleCallbacks = null
            }
            
            (webView.parent as? ViewGroup)?.removeView(webView)
            
            webView.cleanupCallbacksAndDestroy()
            Log.i("HSWebViewWrapper", "WebView cleaned up successfully.")
        } catch (e: Exception) {
            // Log but don't crash - cleanup is best effort
            e.printStackTrace()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Also cleanup when detached from window
        cleanupWebView()
    }

    companion object {
        /**
         * A helper to get react tag id by given WebView
         */
        @JvmStatic
        fun getReactTagFromWebView(webView: WebView): Int {
            // It is expected that the webView is enclosed by [RNCWebViewWrapper] as the first child.
            // Therefore, it must have a parent, and the parent ID is the reactTag.
            // In exceptional cases, such as receiving WebView messaging after the view has been unmounted,
            // the WebView will not have a parent.
            // In this case, we simply return -1 to indicate that it was not found.
            return (webView.parent as? View)?.id ?: -1
        }
    }
}
