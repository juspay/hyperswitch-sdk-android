package io.hyperswitch.devicedatacollection

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

class DeviceDataCollectionWebView(
    private val url: String,
    private val timeoutMs: Int,
    private val activity: Activity,
    private val onResult: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackInvoked = AtomicBoolean(false)
    private var webView: WebView? = null
    private var timeoutRunnable: Runnable? = null

    fun startFlow() {
        if (timeoutMs <= 0 || url.isBlank()) {
            onResult("")
            return
        }
        mainHandler.post { setup() }
    }

    private fun invokeCallback(result: String) {
        if (callbackInvoked.compareAndSet(false, true)) {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            mainHandler.post {
                webView?.let {
                    try {
                        (it.parent as? ViewGroup)?.removeView(it)
                        it.stopLoading()
                        it.destroy()
                    } catch (_: Exception) {}
                }
                webView = null
            }
            onResult(result)
        }
    }

    private fun setup() {
        val wv = WebView(activity)
        wv.settings.javaScriptEnabled = true

        val bridge = object : Any() {
            @JavascriptInterface
            fun onMessage(data: String) = invokeCallback(data)
        }
        wv.addJavascriptInterface(bridge, "HyperDDCBridge")

        wv.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = ViewGroup.LayoutParams(1, 1)
            translationX = -9999f
            translationY = -9999f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        activity.findViewById<ViewGroup>(android.R.id.content).addView(wv)
        webView = wv

        val wrapperHtml = """
            <html><body>
            <iframe src="$url" style="display:none;width:1px;height:1px;"></iframe>
            <script>
            window.addEventListener('message', function(event) {
              var str = typeof event.data === 'string' ? event.data : JSON.stringify(event.data);
              try { HyperDDCBridge.onMessage(str); } catch(e) {}
            });
            </script>
            </body></html>
        """.trimIndent()
        wv.loadDataWithBaseURL(url, wrapperHtml, "text/html", "UTF-8", null)

        timeoutRunnable = Runnable { invokeCallback("") }.also {
            mainHandler.postDelayed(it, timeoutMs.toLong())
        }
    }
}
