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
 * Loads a URL in a hidden iframe inside a hidden WebView and returns the frame's result, shared by
 * the native SDK and the RN wrapper (used for 3DS device data collection). One instance is one run.
 *
 * Loads `url` in the iframe and settles once with the iframe's `next_action` message (JSON)
 * or `""` on failure, timeout, renderer death or [close]. The WebView never navigates its main
 * frame; any redirect is returned to the caller, which opens it visibly. Settling destroys the
 * WebView, so callers only need [close] to stop a run early.
 */
class HiddenIframe(private val activityProvider: () -> Activity?) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val opened = AtomicBoolean(false)
    private val settled = AtomicBoolean(false)
    // close() may come from any thread.
    @Volatile private var onResult: ((String) -> Unit)? = null
    @Volatile private var timeoutRunnable: Runnable? = null

    // Main thread only.
    private var webViewWrapper: HSWebViewWrapper? = null

    /**
     * Opens the iframe. [activityProvider] is read on the main thread; [onResult] is called exactly once.
     * An instance runs once: a second call settles with `""` at once.
     */
    fun open(url: String, timeoutMs: Double, onResult: (String) -> Unit) {
        if (!opened.compareAndSet(false, true)) {
            onResult("")
            return
        }
        this.onResult = onResult
        val iframeOrigin = webOrigin(url)
        if (!timeoutMs.isFinite() || timeoutMs <= 0 || iframeOrigin == null) {
            settle("")
            return
        }
        mainHandler.post { load(url, timeoutMs, iframeOrigin) }
    }

    /** Stops the run and destroys the WebView; if it has not settled yet, `onResult` gets `""`. */
    fun close() {
        settle("")
    }

    private fun settle(result: String) {
        if (!settled.compareAndSet(false, true)) return
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.post { destroyWebView() }
        onResult?.invoke(result)
        onResult = null
    }

    private fun destroyWebView(rendererGone: Boolean = false) {
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

    private fun load(url: String, timeoutMs: Double, iframeOrigin: String) {
        if (settled.get()) return
        val activity = activityProvider() ?: return settle("")
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return settle("")

        val manager = HSWebViewManagerImpl(activity, Callback { _ -> })
        val client = HiddenIframeClient {
            destroyWebView(rendererGone = true)
            settle("")
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
        val resolvedWrapper = wrapper ?: return settle("")
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
                // As on web: any message carrying next_action settles the run, failing unless it is an http(s) redirect.
                if (!json.has("next_action")) return@addWebMessageListener
                val action = json.optJSONObject("next_action")
                val redirect = action?.opt("url") as? String
                val isValidRedirect = action?.opt("type") == "redirect_to_url" &&
                    redirect != null && webOrigin(redirect) != null
                settle(if (isValidRedirect) json.toString() else "")
            }
        } catch (_: Exception) {
            return settle("")
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

        timeoutRunnable = Runnable { settle("") }.also {
            mainHandler.postDelayed(it, timeoutMs.toLong())
        }
    }

    private companion object {
        const val BRIDGE_NAME = "HyperIframeBridge"

        /** Normalised `scheme://host[:port]` for http(s) URLs without user info; null for anything else. */
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
    }

    /**
     * Keeps [HSWebViewClient]'s common handling and adds this view's rules: the main frame never
     * navigates (the iframe may), and a dead renderer settles the run at once instead of at timeout.
     */
    private class HiddenIframeClient(private val onRendererGone: () -> Unit) : HSWebViewClient() {

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
