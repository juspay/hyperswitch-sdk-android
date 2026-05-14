package io.hyperswitch.click_to_pay

import android.app.Activity
import android.view.ViewGroup
import androidx.webkit.WebViewCompat
import io.hyperswitch.click_to_pay.models.ClickToPayErrorType
import io.hyperswitch.click_to_pay.models.ClickToPayException
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogType
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages WebView lifecycle and operations for Click to Pay.
 *
 * This class handles all WebView-related concerns including initialization,
 * attachment/detachment, correlation ID tracking, and lifecycle state management.
 */
internal class ClickToPayWebViewManager(
    private var activity: Activity,
    private val logger: (type: LogType, eventName: EventName, value: String, category: LogCategory) -> Unit
) {
    private lateinit var webViewManager: HSWebViewManagerImpl
    private lateinit var webViewWrapper: HSWebViewWrapper

    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()
    private val correlationIds = mutableSetOf<String>()
    private val captureCorrelationIds = AtomicBoolean(true)

    private val isWebViewInitialized = AtomicBoolean(false)
    private val isWebViewAttached = AtomicBoolean(false)
    private val lifecycleMutex = Mutex()

    private val onMessageCallback = Callback { args ->
        (args["data"] as? String)?.let { jsonString ->
            val requestId = JSONObject(jsonString).optString("requestId", "")
            if (requestId.isNotEmpty()) {
                pendingRequests.remove(requestId)?.resume(jsonString) {}
            }
        }
    }

    /**
     * Checks if the WebView provider is available on this device.
     */
    fun isWebViewAvailable(): Boolean {
        return try {
            WebViewCompat.getCurrentWebViewPackage(activity) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true if the WebView has been initialized.
     */
    fun isInitialized(): Boolean = isWebViewInitialized.get()

    /**
     * Returns the WebView wrapper instance.
     */
    fun getWebViewWrapper(): HSWebViewWrapper = webViewWrapper

    /**
     * Returns the WebView manager instance.
     */
    fun getWebViewManager(): HSWebViewManagerImpl = webViewManager

    /**
     * Returns the current set of captured correlation IDs.
     */
    fun getCorrelationIds(): Set<String> = correlationIds.toSet()

    /**
     * Clears all captured correlation IDs.
     */
    fun clearCorrelationIds() {
        correlationIds.clear()
    }

    /**
     * Starts capturing correlation IDs from network requests.
     */
    fun startCapturingCorrelationIds() {
        captureCorrelationIds.set(true)
    }

    /**
     * Stops capturing correlation IDs.
     */
    fun stopCapturingCorrelationIds() {
        captureCorrelationIds.set(false)
    }

    /**
     * Registers a pending request continuation for async JS communication.
     */
    fun registerPendingRequest(requestId: String, continuation: CancellableContinuation<String>) {
        pendingRequests[requestId] = continuation
    }

    /**
     * Removes a pending request without resuming it.
     */
    fun removePendingRequest(requestId: String) {
        pendingRequests.remove(requestId)
    }

    /**
     * Cancels all pending requests.
     */
    fun cancelPendingRequests(errorMessage: String = "Operation cancelled") {
        val snapshot = pendingRequests.keys.toList()
        if (snapshot.isEmpty()) return
        logger(LogType.DEBUG, EventName.WEBVIEW, "Cancelling ${snapshot.size} pending requests", LogCategory.USER_EVENT)
        for (key in snapshot) {
            pendingRequests.remove(key)?.cancel(kotlinx.coroutines.CancellationException(errorMessage))
        }
    }

    /**
     * Resumes a pending continuation with the given value.
     */
    fun resumePendingRequest(requestId: String, value: String) {
        pendingRequests.remove(requestId)?.resume(value) {}
    }

    /**
     * Resets the internal state for reinitialization.
     */
    fun resetState() {
        isWebViewInitialized.set(false)
        isWebViewAttached.set(false)
        correlationIds.clear()
        pendingRequests.clear()
    }

    /**
     * Initializes the WebView components asynchronously.
     *
     * @param allowReinitialize Whether to allow reinitializing an already initialized WebView
     * @throws ClickToPayException if WebView initialization fails
     */
    suspend fun ensureInitialized(allowReinitialize: Boolean = false) {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && !allowReinitialize) return@withLock

            logger(LogType.DEBUG, EventName.CREATE_WEBVIEW_INIT, "creating webview", LogCategory.USER_EVENT)

            try {
                if (!isWebViewAvailable()) {
                    logger(LogType.ERROR, EventName.CREATE_WEBVIEW_RETURNED, "WebView provider unavailable", LogCategory.USER_ERROR)
                    throw IllegalStateException("WebView provider unavailable")
                }

                repeat(2) { attempt ->
                    try {
                        withContext(Dispatchers.Main) {
                            initializeWebViewInternal()
                        }
                        isWebViewInitialized.set(true)
                        isWebViewAttached.set(true)
                        logger(LogType.DEBUG, EventName.CREATE_WEBVIEW_RETURNED, "webview created", LogCategory.USER_EVENT)
                        return@withLock
                    } catch (t: Throwable) {
                        logger(LogType.ERROR, EventName.CREATE_WEBVIEW_RETURNED, "Attempt $attempt failed", LogCategory.USER_ERROR)
                        if (attempt == 1) throw t
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                logger(LogType.ERROR, EventName.CREATE_WEBVIEW_RETURNED, "Failed to create webview: ${e.message}", LogCategory.USER_ERROR)
                throw ClickToPayException("Unable to initialize ClickToPay: ${e.message}", "WEBVIEW_ERROR")
            }
        }
    }

    /**
     * Internal WebView initialization on the main thread.
     */
    private fun initializeWebViewInternal() {
        webViewManager = HSWebViewManagerImpl(activity, onMessageCallback)
        webViewWrapper = webViewManager.createViewInstance()

        webViewManager.setJavaScriptEnabled(webViewWrapper, true)
        webViewManager.setMessagingEnabled(webViewWrapper, true)
        webViewManager.setJavaScriptCanOpenWindowsAutomatically(webViewWrapper, true)
        webViewManager.setScalesPageToFit(webViewWrapper, true)
        webViewManager.setMixedContentMode(webViewWrapper, "always")
        webViewManager.setThirdPartyCookiesEnabled(webViewWrapper, true)
        webViewManager.setCacheEnabled(webViewWrapper, true)

        webViewWrapper.webView.setRequestInterceptor { data ->
            try {
                val headers = data["headers"] as? Map<*, *>
                val correlationId = headers?.get("X-CORRELATION-ID")?.toString()
                if (correlationId != null && captureCorrelationIds.get()) {
                    correlationIds.add(correlationId)
                }
            } catch (_: Exception) {
                // Ignore interceptor errors
            }
        }

        webViewWrapper.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)
            contentDescription = "Click to Pay"
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        activity.findViewById<ViewGroup>(android.R.id.content).addView(webViewWrapper)
    }

    /**
     * Updates the activity reference and reattaches the WebView.
     */
    suspend fun updateActivity(newActivity: Activity) {
        if (activity === newActivity) return

        lifecycleMutex.withLock {
            activity = newActivity

            if (isWebViewInitialized.get() && isWebViewAttached.get()) {
                withContext(Dispatchers.Main) {
                    (webViewWrapper.parent as? ViewGroup)?.removeView(webViewWrapper)
                    val rootView = newActivity.findViewById<ViewGroup>(android.R.id.content)
                        ?: throw IllegalStateException("Failed to find root view")
                    rootView.addView(webViewWrapper)
                }
            }
        }
    }

    /**
     * Detaches the WebView from the view hierarchy.
     */
    suspend fun detach() {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && isWebViewAttached.get()) {
                withContext(Dispatchers.Main) {
                    (webViewWrapper.parent as? ViewGroup)?.removeView(webViewWrapper)
                }
                isWebViewAttached.set(false)
                logger(LogType.DEBUG, EventName.WEBVIEW, "webview detached, JS execution paused", LogCategory.USER_EVENT)
            }
        }
    }

    /**
     * Reattaches the WebView to the view hierarchy.
     */
    suspend fun reattach() {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && !isWebViewAttached.get()) {
                withContext(Dispatchers.Main) {
                    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                    rootView.addView(webViewWrapper)
                }
                isWebViewAttached.set(true)
                logger(LogType.DEBUG, EventName.WEBVIEW, "webview reattached, JS execution resumed", LogCategory.USER_EVENT)
            }
        }
    }

    /**
     * Destroys the WebView and cleans up resources.
     */
    suspend fun destroy() {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get()) {
                withContext(Dispatchers.Main) {
                    (webViewWrapper.parent as? ViewGroup)?.removeView(webViewWrapper)
                    webViewWrapper.webView.destroy()
                }
                isWebViewInitialized.set(false)
                isWebViewAttached.set(false)
            }
        }
    }

    /**
     * Evaluates JavaScript code in the WebView and returns the response.
     *
     * @param jsCode The JavaScript code to execute
     * @return The JSON response string from the WebView
     */
    suspend fun evaluateJavaScript(jsCode: String): String {
        val requestId = UUID.randomUUID().toString()
        return evaluateJavaScript(jsCode, requestId)
    }

    /**
     * Evaluates JavaScript code in the WebView with a specific request ID.
     *
     * @param jsCode The JavaScript code to execute
     * @param requestId Unique identifier for tracking this request
     * @return The JSON response string from the WebView
     */
    suspend fun evaluateJavaScript(jsCode: String, requestId: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                registerPendingRequest(requestId, continuation)
                continuation.invokeOnCancellation { removePendingRequest(requestId) }
                webViewManager.evaluateJavascriptWithFallback(webViewWrapper, jsCode)
            }
        }
    }
}
