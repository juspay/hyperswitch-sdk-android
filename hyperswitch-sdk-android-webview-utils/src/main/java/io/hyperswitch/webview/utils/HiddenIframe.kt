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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads a URL in a hidden iframe inside a hidden WebView and returns the frame's result, shared by
 * the native SDK and the RN wrapper (used for 3DS device data collection). One instance is one run.
 *
 * Loads `url` in the iframe and settles once with the iframe's `next_action` message (JSON)
 * or `""` on failure, timeout, renderer death or [close]. The WebView never navigates its main
 * frame; any redirect is returned to the caller, which opens it visibly. Settling destroys the
 * WebView, so callers only need [close] to stop a run early.
 */
class HiddenIframe(private val activity: Activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val opened = AtomicBoolean(false)
    private val settled = AtomicBoolean(false)
    // Taken once with getAndSet(null) by whichever of settle() and open() comes last, since close() may run on any thread.
    private val onResult = AtomicReference<((String) -> Unit)?>(null)
    @Volatile private var timeoutRunnable: Runnable? = null

    // Main thread only.
    private var webViewWrapper: HSWebViewWrapper? = null

    /**
     * Opens the iframe. [onResult] is called exactly once.
     * An instance runs once: a second call settles with `""` at once.
     */
    fun open(url: String, timeoutMs: Double, onResult: (String) -> Unit) {
        if (!opened.compareAndSet(false, true)) {
            onResult(FAILURE_RESULT)
            return
        }
        this.onResult.set(onResult)
        // close() may have run before or during open(): deliver its "" now.
        if (settled.get()) {
            this.onResult.getAndSet(null)?.invoke(FAILURE_RESULT)
            return
        }
        val iframeOrigin = HSWebViewUtils.httpsOrigin(url)
        if (!timeoutMs.isFinite() || timeoutMs <= 0 || iframeOrigin == null) {
            fail()
            return
        }
        mainHandler.post { load(url, timeoutMs, iframeOrigin) }
    }

    /**
     * Stops the run and destroys the WebView; if it has not settled yet, `onResult` gets `""`
     * (also when called before [open]).
     */
    fun close() {
        fail()
    }

    // Every failure goes through here, so what the caller receives on failure is decided in one place.
    private fun fail() = settle(FAILURE_RESULT)

    private fun settle(result: String) {
        if (!settled.compareAndSet(false, true)) return
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.post { destroyWebView() }
        onResult.getAndSet(null)?.invoke(result)
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
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return fail()

        val manager = HSWebViewManagerImpl(activity, Callback { _ -> })
        val client = HiddenIframeClient {
            destroyWebView(rendererGone = true)
            fail()
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
        val resolvedWrapper = wrapper ?: return fail()
        webViewWrapper = resolvedWrapper

        manager.setJavaScriptEnabled(resolvedWrapper, true)
        // A popup would be a second hidden WebView that cleanup never destroys.
        manager.setSetSupportMultipleWindows(resolvedWrapper, false)

        try {
            WebViewCompat.addWebMessageListener(
                resolvedWrapper.webView, BRIDGE_NAME, setOf(iframeOrigin),
            ) { _, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame || HSWebViewUtils.httpsOrigin(sourceOrigin.toString()) != iframeOrigin ||
                    message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
                val data = message.data ?: return@addWebMessageListener
                val json = try { JSONObject(data) } catch (_: Exception) { return@addWebMessageListener }
                // Any message carrying next_action settles the run, failing unless it is an https redirect (web also allows http).
                if (!json.has("next_action")) return@addWebMessageListener
                val action = json.optJSONObject("next_action")
                val redirect = action?.opt("url") as? String
                val isValidRedirect = action?.opt("type") == "redirect_to_url" &&
                    HSWebViewUtils.validateHttpsUrl(redirect)
                if (isValidRedirect) settle(json.toString()) else fail()
            }
        } catch (_: Exception) {
            return fail()
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

        timeoutRunnable = Runnable { fail() }.also {
            mainHandler.postDelayed(it, timeoutMs.toLong())
        }
    }

    private companion object {
        const val BRIDGE_NAME = "HyperIframeBridge"

        // Client-core (both bundle paths) maps "" to ddc_failure; change it here once it accepts a reason.
        const val FAILURE_RESULT = ""
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
