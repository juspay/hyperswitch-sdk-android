package io.hyperswitch.lite

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Singleton manager for WebViewHostActivity to enable preloading
 * and warming up WebView for better performance.
 */
object WebViewHostActivityManager {
    
    private var isPreloaded = false
    
    /**
     * Preload and warm up WebView for faster launch
     */
    fun preload(activity: Activity) {
        if (!isPreloaded) {
            // Warm up WebView in background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Create a dummy WebView to trigger WebView initialization
                    val dummyWebView = WebView(activity.applicationContext)
                    dummyWebView.loadUrl("about:blank")
                    dummyWebView.destroy()
                } catch (e: Exception) {
                    // Ignore any errors during preloading
                }
            }
            isPreloaded = true
        }
    }
    
    /**
     * Launch WebViewHostActivity with the given request body
     */
    fun launch(activity: Activity, requestBody: String) {
        val intent = Intent(activity, WebViewHostActivity::class.java).apply {
            putExtra("requestBody", requestBody)
            // Use clear task flags to ensure proper activity stack management
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        activity.startActivity(intent)
    }
    
    /**
     * Clear the preloaded state
     */
    fun clear() {
        isPreloaded = false
    }
}
