package io.hyperswitch.react.devicedatacollection

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.facebook.react.bridge.Callback
import io.hyperswitch.webview.utils.Callback as HSCallback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import java.util.concurrent.atomic.AtomicBoolean

internal class DeviceDataCollectionWebView(
    private val url: String,
    private val timeoutMs: Int,
    private val activity: Activity,
    private val callback: Callback,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackInvoked = AtomicBoolean(false)
    private var webViewWrapper: HSWebViewWrapper? = null
    private var timeoutRunnable: Runnable? = null

    internal fun startFlow() {
        if (timeoutMs <= 0 || url.isBlank()) {
            callback.invoke("")
            return
        }
        mainHandler.post { setup() }
    }

    private fun invokeCallback(result: String) {
        if (callbackInvoked.compareAndSet(false, true)) {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            mainHandler.post {
                webViewWrapper?.let { wrapper ->
                    try {
                        (wrapper.parent as? ViewGroup)?.removeView(wrapper)
                        wrapper.webView.stopLoading()
                        wrapper.webView.destroy()
                    } catch (_: Exception) {}
                }
                webViewWrapper = null
            }
            callback.invoke(result)
        }
    }

    private fun setup() {
        val manager = HSWebViewManagerImpl(activity, HSCallback { _ -> })

        var wrapper: HSWebViewWrapper? = null
        repeat(2) { attempt ->
            if (wrapper != null) return@repeat
            try {
                wrapper = manager.createViewInstance()
            } catch (_: Exception) {
                if (attempt == 0) Thread.sleep(200)
            }
        }
        val resolvedWrapper = wrapper ?: run {
            invokeCallback("")
            return
        }

        manager.setJavaScriptEnabled(resolvedWrapper, true)

        val bridge = object : Any() {
            @android.webkit.JavascriptInterface
            fun onMessage(data: String) = invokeCallback(data)
        }
        resolvedWrapper.webView.addJavascriptInterface(bridge, "HyperDDCBridge")

//            resolvedWrapper.webView.webViewClient = object : WebViewClient() {
//                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
//                    if (request.isForMainFrame) {
//                        val url = request.url.toString()
//                        Log.d("HyperDDC", "shouldOverride intercepted: $url")
//                        invokeCallback("{\"next_action\":{\"type\":\"redirect_to_url\",\"url\":\"$url\"}}")
//                        return true
//                    }
//                    return false
//                }
//
//                @Suppress("DEPRECATION")
//                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                    Log.d("HyperDDC", "shouldOverride intercepted (legacy): $url")
//                    invokeCallback("{\"next_action\":{\"type\":\"redirect_to_url\",\"url\":\"$url\"}}")
//                    return true
//                }
//            }

        resolvedWrapper.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = ViewGroup.LayoutParams(1, 1)
            translationX = -9999f
            translationY = -9999f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        activity.findViewById<ViewGroup>(android.R.id.content).addView(resolvedWrapper)
        webViewWrapper = resolvedWrapper

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
        resolvedWrapper.webView.loadDataWithBaseURL(url, wrapperHtml, "text/html", "UTF-8", null)

        timeoutRunnable = Runnable { invokeCallback("") }.also {
            mainHandler.postDelayed(it, timeoutMs.toLong())
        }
    }
}
