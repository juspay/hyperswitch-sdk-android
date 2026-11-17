package io.hyperswitch.webview.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hidden WebView for 3DS device data collection (DDC), shared by the native SDK and the RN wrapper.
 *
 * Loads `url` in a hidden iframe and settles once with the iframe's `next_action` message (JSON)
 * or `""` on failure, timeout or renderer death. The WebView never navigates its main frame; any
 * redirect is returned to the caller, which opens it visibly.
 */
object DeviceDataCollectionWebView {

    private const val BRIDGE_NAME = "HyperDDCBridge"

    /** Normalised `scheme://host[:port]` for http(s) URLs without user info; null for anything else. */
    @JvmStatic
    fun webOrigin(value: String): String? {
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme != "https" && scheme != "http") || uri.rawUserInfo != null ||
                (uri.port != -1 && uri.port !in 1..65535)) return null
            val defaultPort = if (scheme == "https") 443 else 80
            val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Starts DDC. [activityProvider] is read on the main thread; [onResult] is called exactly once.
     */
    @JvmStatic
    fun open(
        activityProvider: () -> Activity?,
        url: String,
        timeoutMs: Double,
        onResult: (String) -> Unit,
    ) {
        val iframeOrigin = webOrigin(url)
        if (!timeoutMs.isFinite() || timeoutMs <= 0 || iframeOrigin == null) {
            onResult("")
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val callbackInvoked = AtomicBoolean(false)
        var webViewWrapper: HSWebViewWrapper? = null
        var timeoutRunnable: Runnable? = null

        fun cleanupWebView(rendererGone: Boolean = false) {
            val wrapper = webViewWrapper ?: return
            webViewWrapper = null
            try {
                try {
                    (wrapper.parent as? ViewGroup)?.removeView(wrapper)
                    if (!rendererGone) wrapper.webView.stopLoading()
                } finally {
                    wrapper.webView.destroy()
                }
            } catch (_: Exception) {
            }
        }

        val invokeCallback = { result: String ->
            if (callbackInvoked.compareAndSet(false, true)) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                mainHandler.post { cleanupWebView() }
                onResult(result)
            }
        }

        mainHandler.post {
            val activity = activityProvider() ?: run {
                invokeCallback("")
                return@post
            }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                invokeCallback("")
                return@post
            }

            val manager = HSWebViewManagerImpl(activity, Callback { _ -> })
            val client = DDCWebViewClient {
                cleanupWebView(rendererGone = true)
                invokeCallback("")
            }

            var wrapper: HSWebViewWrapper? = null
            repeat(2) { attempt ->
                if (wrapper != null) return@repeat
                try {
                    wrapper = manager.createViewInstance(client)
                } catch (_: Exception) {
                    if (attempt == 0) Thread.sleep(200)
                }
            }
            val resolvedWrapper = wrapper ?: run {
                invokeCallback("")
                return@post
            }
            webViewWrapper = resolvedWrapper

            manager.setJavaScriptEnabled(resolvedWrapper, true)
            // A popup would be a second hidden WebView that cleanup never destroys.
            manager.setSetSupportMultipleWindows(resolvedWrapper, false)

            try {
                WebViewCompat.addWebMessageListener(
                    resolvedWrapper.webView, BRIDGE_NAME, setOf(iframeOrigin),
                ) { _, message, sourceOrigin, isMainFrame, _ ->
                    if (!isMainFrame || webOrigin(sourceOrigin.toString()) != iframeOrigin ||
                        message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
                    val data = message.data ?: return@addWebMessageListener
                    val json = try { JSONObject(data) } catch (_: Exception) { return@addWebMessageListener }
                    // As on web: any message carrying next_action settles DDC, failing unless it is an http(s) redirect.
                    if (!json.has("next_action")) return@addWebMessageListener
                    val action = json.optJSONObject("next_action")
                    val redirect = action?.opt("url") as? String
                    val isValidRedirect = action?.opt("type") == "redirect_to_url" &&
                        redirect != null && webOrigin(redirect) != null
                    invokeCallback(if (isValidRedirect) json.toString() else "")
                }
            } catch (_: Exception) {
                invokeCallback("")
                return@post
            }

            resolvedWrapper.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(1, 1)
                translationX = -9999f
                translationY = -9999f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }

            activity.findViewById<ViewGroup>(android.R.id.content).addView(resolvedWrapper)

            val wrapperHtml = """
                <html><body>
                <iframe id="ddc-frame" src="${TextUtils.htmlEncode(url)}" style="display:none;width:1px;height:1px;"></iframe>
                <script>
                window.addEventListener('message', function(event) {
                  if (event.source !== document.getElementById('ddc-frame').contentWindow ||
                      event.origin !== ${JSONObject.quote(iframeOrigin)}) return;
                  try {
                    var str = typeof event.data === 'string' ? event.data : JSON.stringify(event.data);
                    if (typeof str === 'string') $BRIDGE_NAME.postMessage(str);
                  } catch(e) {}
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

    /**
     * Keeps [HSWebViewClient]'s common handling and adds DDC's rules: the main frame never
     * navigates (the iframe may), and a dead renderer settles DDC at once instead of at timeout.
     */
    private class DDCWebViewClient(private val onRendererGone: () -> Unit) : HSWebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

        // Backstop for top-level navigations shouldOverrideUrlLoading never sees (e.g. POST form submits).
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            if (request.isForMainFrame) {
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            } else {
                super.shouldInterceptRequest(view, request)
            }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            super.onRenderProcessGone(view, detail)
            // A dead renderer's WebView must be destroyed without calling stopLoading().
            onRendererGone()
            return true
        }
    }
}
