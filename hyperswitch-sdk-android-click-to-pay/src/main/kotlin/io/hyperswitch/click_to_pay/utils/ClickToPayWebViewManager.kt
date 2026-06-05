package io.hyperswitch.click_to_pay.utils

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import androidx.webkit.WebViewCompat
import io.hyperswitch.click_to_pay.models.ClickToPayErrorType
import io.hyperswitch.click_to_pay.models.ClickToPayException
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogType
import io.hyperswitch.webview.utils.Arguments
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
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.coroutines.resume

class ClickToPayWebViewManager(
    private var activity: Activity,
    private var logger: ((LogType, EventName, String, LogCategory) -> Unit)? = null
) {

    fun setLogger(logger: (LogType, EventName, String, LogCategory) -> Unit) {
        this.logger = logger
        ClickToPayModelParser.Companion.setLogger(logger)
    }

    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()
    private val isWebViewInitialized = AtomicBoolean(false)
    private val isWebViewAttached = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    private val lifecycleMutex = Mutex()

    private val originalAccessibility = HashMap<View, Int>()


    private fun resumeContinuation(requestId: String, value: String) {
        pendingRequests.remove(requestId)?.resume(value)
    }

    private fun initializeWebViewInternal() {
        val onMessage = Callback { args ->
            (args["data"] as? String)?.let { jsonString ->
                val requestId = JSONObject(jsonString).optString("requestId", "")
                if (requestId.isNotEmpty()) {
                    resumeContinuation(requestId, jsonString)
                }
            }
        }

        hSWebViewManagerImpl = HSWebViewManagerImpl(activity, onMessage)
        hSWebViewWrapper = hSWebViewManagerImpl.createViewInstance()

        hSWebViewManagerImpl.setJavaScriptEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setMessagingEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setJavaScriptCanOpenWindowsAutomatically(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setScalesPageToFit(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setMixedContentMode(hSWebViewWrapper, "compatibility")
        hSWebViewManagerImpl.setThirdPartyCookiesEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setCacheEnabled(hSWebViewWrapper, true)
        hSWebViewWrapper.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = LayoutParams(1, 1)
            contentDescription = "Click to Pay"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        activity.findViewById<ViewGroup>(android.R.id.content).addView(hSWebViewWrapper)
    }

    fun captureCorrelationIds(callback: (String) -> Unit) {
        hSWebViewWrapper.webView.setRequestInterceptor { data ->
            try {
                val headers = data["headers"] as? Map<*, *>
                val correlationId = headers?.get("X-CORRELATION-ID")?.toString()
                callback(correlationId ?: "")
            } catch (_: Exception) {
            }
        }
    }

    private fun isWebViewAvailable(): Boolean {
        return try {
            WebViewCompat.getCurrentWebViewPackage(activity) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Helper function to execute JavaScript on the Main thread and return the response.
     */
    suspend fun evaluateJavascriptOnMainThread(requestId: String, jsCode: String): String {
        return withContext(Dispatchers.Main) {
            if (isDestroyed.get()) {
                throw ClickToPayException(
                    "ClickToPay session has been destroyed",
                    "SESSION_DESTROYED"
                )
            }
            suspendCancellableCoroutine { continuation ->
                if (isDestroyed.get()) {
                    continuation.resumeWith(
                        Result.failure(
                            ClickToPayException(
                                "ClickToPay session has been destroyed",
                                "SESSION_DESTROYED"
                            )
                        )
                    )
                    return@suspendCancellableCoroutine
                }
                pendingRequests[requestId] = continuation
                continuation.invokeOnCancellation {
                    pendingRequests.remove(requestId)
                }
                hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
            }
        }
    }

    /**
     * Sets modal accessibility mode by hiding all views except the target view.
     */
    fun setModalAccessibility(root: ViewGroup, targetView: View) {
        val ancestors = HashSet<View>()
        var parent = targetView.parent
        while (parent is View) {
            ancestors.add(parent as View)
            parent = parent.parent
        }
        hideViewsRecursively(root, targetView, ancestors)
    }

    /**
     * Recursively hides views from accessibility services while preserving
     * the target view and its ancestor chain.
     */
    private fun hideViewsRecursively(
        currentView: View, targetView: View, ancestors: HashSet<View>
    ) {
        if (currentView == targetView) return
        if (ancestors.contains(currentView)) {
            if (currentView is ViewGroup) {
                for (i in 0 until currentView.childCount) {
                    hideViewsRecursively(currentView.getChildAt(i), targetView, ancestors)
                }
            }
        } else {
            if (!originalAccessibility.containsKey(currentView)) {
                originalAccessibility[currentView] = currentView.importantForAccessibility
            }
            currentView.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /**
     * Restores the original accessibility settings for all modified views.
     */
    fun restoreAccessibility() {
        for ((view, originalValue) in originalAccessibility) {
            view.importantForAccessibility = originalValue
        }
        originalAccessibility.clear()
    }

    /**
     * Detaches the WebView from the view hierarchy to pause JavaScript execution.
     * This triggers the same lifecycle behavior as backgrounding an app,
     * automatically pausing timers, network requests, and all JavaScript operations.
     */
    suspend fun detachWebView() {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && isWebViewAttached.get()) {
                withContext(Dispatchers.Main) {
                    val parent = hSWebViewWrapper.parent
                    if (parent is ViewGroup) {
                        parent.removeView(hSWebViewWrapper)
                    }
                }
                isWebViewAttached.set(false)
                logger?.invoke(
                    LogType.DEBUG, EventName.WEBVIEW, "webview de-attached JS execution paused",
                    LogCategory.USER_EVENT
                )
            }
        }

    }

    /**
     * Reattaches the WebView to the view hierarchy to resume JavaScript execution.
     * This automatically resumes all paused operations.
     */
    private suspend fun reattachWebView() {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && !isWebViewAttached.get()) {
                withContext(Dispatchers.Main) {
                    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                    rootView.addView(hSWebViewWrapper)
                }
                isWebViewAttached.set(true)
                logger?.invoke(
                    LogType.DEBUG, EventName.WEBVIEW, "webview reattached  JS execution resumed",
                    LogCategory.USER_EVENT
                )
            }
        }
    }

    /**
     * Cancels all pending requests to prevent stale callbacks from executing.
     */
    fun cancelPendingRequests(errorMessage: String = "Operation cancelled due to error") {
        val snapshot = pendingRequests.keys.toList()
        if (snapshot.isEmpty()) return
        logger?.invoke(LogType.DEBUG, EventName.WEBVIEW, "Cancelling ${snapshot.size} pending requests", LogCategory.USER_EVENT)
        for (key in snapshot) {
            pendingRequests.remove(key)
                ?.cancel(kotlinx.coroutines.CancellationException(errorMessage))
        }
    }

    /**
     * Ensures the instance has not been destroyed.
     */
    private fun ensureNotDestroyed() {
        if (isDestroyed.get()) {
            logger?.invoke(LogType.DEBUG, EventName.WEBVIEW, "Webview destroyed", LogCategory.USER_EVENT)
            throw ClickToPayException(
                "ClickToPaySessionLauncher has been destroyed and cannot be used",
                ClickToPayErrorType.INSTANCE_DESTROYED
            )
        }
    }

    /**
     * Ensures the session is ready for operations.
     */
    suspend fun ensureReady() {
        ensureNotDestroyed()
        ensureWebViewInitialized()
        reattachWebView()
    }

    /**
     * Initializes the WebView components asynchronously on the main thread.
     */
    suspend fun ensureWebViewInitialized(
        allowReinitialize: Boolean = false
    ) {
        lifecycleMutex.withLock {
            if (isWebViewInitialized.get() && !allowReinitialize) return@withLock
            logger?.invoke(
                LogType.DEBUG,
                EventName.CREATE_WEBVIEW_INIT,
                "creating webview",
                LogCategory.USER_EVENT
            )
            try {
                if (!isWebViewAvailable()) {
                    logger?.invoke(
                        LogType.ERROR,
                        EventName.CREATE_WEBVIEW_RETURNED,
                        "WebView provider unavailable",
                        LogCategory.USER_ERROR
                    )
                    throw IllegalStateException("WebView provider unavailable")
                }
                // Retry WebView creation once (important for Android 15/16 bug)
                repeat(2) { attempt ->
                    try {
                        withContext(Dispatchers.Main) {
                            initializeWebViewInternal()
                        }
                        isWebViewInitialized.set(true)
                        isWebViewAttached.set(true)
                        logger?.invoke(
                            LogType.DEBUG,
                            EventName.CREATE_WEBVIEW_RETURNED,
                            "webview created",
                            LogCategory.USER_EVENT
                        )
                        return@withLock
                    } catch (t: Throwable) {
                        logger?.invoke(
                            LogType.ERROR,
                            EventName.CREATE_WEBVIEW_RETURNED,
                            "Attempted to create = $attempt",
                            LogCategory.USER_ERROR
                        )
                        if (attempt == 1) throw t
                        delay(200) // retry delay
                    }
                }
            } catch (e: Exception) {
                logger?.invoke(
                    LogType.ERROR,
                    EventName.CREATE_WEBVIEW_RETURNED,
                    "Failed to create webview ${e.message}",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Unable to initialize ClickToPay: ${e.message}",
                    "WEBVIEW_ERROR",
                )
            }
        }
    }

    /**
     * Loads HTML source into the WebView and awaits the initialization response.
     *
     * @param html The HTML content to load
     * @param baseUrl The base URL for resolving relative URLs
     * @param requestId Optional request ID for correlating the response
     * @return The JSON response string from the WebView
     */
    suspend fun loadSource(
        html: String,
        baseUrl: String,
        requestId: String = UUID.randomUUID().toString()
    ): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestId] = continuation
                continuation.invokeOnCancellation {
                    pendingRequests.remove(requestId)
                }
                val map = Arguments.createMap()
                map.putString("html", html)
                map.putString("baseUrl", baseUrl)
                hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
            }
        }
    }

    /**
     * Moves the WebView from the current activity to a new activity.
     */
    suspend fun moveToActivity(newActivity: Activity) {
        withContext(Dispatchers.Main) {
            val parent = hSWebViewWrapper.parent
            if (parent is ViewGroup) {
                parent.removeView(hSWebViewWrapper)
            }
            val rootView = newActivity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(hSWebViewWrapper)
        }
        activity = newActivity
    }

    /**
     * Sets modal accessibility for the WebView within the given root view.
     */
    fun setModalAccessibilityForWebView(root: ViewGroup) {
        if (::hSWebViewWrapper.isInitialized) {
            setModalAccessibility(root, hSWebViewWrapper)
        }
    }

    suspend fun closeHyperInstance() {
        try {
            logger?.invoke(
                LogType.DEBUG,
                EventName.CLOSE_HYPER_INSTANCE,
                "",
                LogCategory.USER_EVENT
            )
            ensureReady()
            val requestId = UUID.randomUUID().toString()
            val jsCode =
                "(async function(){try{await Promise.race([window.hyperInstance.deinit(),new Promise(r=>setTimeout(()=>{r.resolve()},5000))]);window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{code:'success'}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'CloseInstanceFailed',message:error.message}}}));}})();"
            val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
            val jsonObject = ClickToPayModelParser.Companion.parseJSONObject(
                responseJson,
                EventName.CLOSE_HYPER_INSTANCE_RETURNED
            )
            val data = ClickToPayModelParser.Companion.getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.CLOSE_HYPER_INSTANCE_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
            }
            logger?.invoke(
                LogType.DEBUG, EventName.CLOSE_HYPER_INSTANCE_RETURNED, "", LogCategory.USER_EVENT
            )
        } catch (e: Exception) {
            logger?.invoke(
                LogType.ERROR,
                EventName.CLOSE_HYPER_INSTANCE_RETURNED,
                "message: ${e.message}",
                LogCategory.USER_EVENT
            )
        }
    }

    /**
     * Closes and destroys the Click to Pay session.
     */
    @Throws(ClickToPayException::class)
    suspend fun close(closeHyper: Boolean = true) {
        try {
            logger?.invoke(
                LogType.DEBUG,
                EventName.CLOSE_INIT,
                "",
                LogCategory.USER_EVENT
            )
            if (closeHyper) {
                closeHyperInstance()
            }
            cancelPendingRequests("session is being closed")
            restoreAccessibility()
            lifecycleMutex.withLock {
                withContext(Dispatchers.Main) {
                    if (isWebViewInitialized.get()) {
                        val parent = hSWebViewWrapper.parent
                        if (parent is ViewGroup) {
                            parent.removeView(hSWebViewWrapper)
                        }
                        hSWebViewWrapper.webView.destroy()
                    }
                }
                isWebViewInitialized.set(false)
                isWebViewAttached.set(false)
                isDestroyed.set(true)
            }
            logger?.invoke(
                LogType.DEBUG,
                EventName.CLOSE_RETURNED,
                "",
                LogCategory.USER_EVENT
            )
        } catch (e: Exception) {
            throw ClickToPayException(
                "Failed to close Click to Pay session: ${e.message}", "CLOSE_ERROR"
            )
        }
    }
}
