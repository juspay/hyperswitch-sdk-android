package io.hyperswitch.lite

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.webview.utils.Arguments
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import io.hyperswitch.webview.utils.ReadableArray
import org.json.JSONObject

open class WebViewFragment : Fragment() {
    private fun isScanCardAvailable(): Boolean =
        try {
            Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

//    private lateinit var webViewContainer: FrameLayout
//    private lateinit var mainWebView: WebView

    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper
    private val webViews = mutableListOf<WebView>()

    // URL
    private lateinit var bundleUrl: String

    private var requestBody: ReadableArray? = null
    private var sdkLoaded: Boolean = false

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bundleUrl = getString(R.string.webViewUrl)
//        mainWebView = createWebView()
//        webViewContainer = FrameLayout(context)
//        webViews.add(mainWebView)
//        webViewContainer.addView(mainWebView)
        hSWebViewManagerImpl = HSWebViewManagerImpl(activity, onMessage)
        hSWebViewWrapper = hSWebViewManagerImpl.createViewInstance()

        hSWebViewManagerImpl.setJavaScriptEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setMessagingEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setScalesPageToFit(hSWebViewWrapper, true)

        loadUrl()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = hSWebViewWrapper // webViewContainer

    private fun loadUrl() {
        val map = Arguments.createMap()
        map.putString("uri", bundleUrl)
        hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
    }

    fun exitPaymentSheet(data: JSONObject) {
        PaymentSheetCallbackManager.executeCallback(data.toString())
        loadUrl()
        activity.fragmentManager
            .beginTransaction()
            .detach(this@WebViewFragment)
            .commit()
    }

    fun launchGPay(data: JSONObject) {
        GooglePayCallbackManager.setCallback(
            context,
            data.toString(),
            ::sendResultToWebView,
        )
    }

//    private fun sendResultToWebView(result: Map<String, Any?>) {
//        try {
//            val javascriptFunction =
//                """window.postMessage(JSON.stringify({"googlePayData":  ${JSONObject(result)}}), '*');""".trimIndent()
//            context.runOnUiThread {
//                webView.evaluateJavascript(javascriptFunction, null)
//            }
//        } catch (e: Exception) {
//            Log.e("sendResultToWebView", "Error sending result to WebView", e)
//        }
//    }

    fun sdkInitialised(data: JSONObject) {
        sdkLoaded = data.getBoolean("sdkLoaded")
        requestBody?.let {
            hSWebViewManagerImpl.receiveCommand(hSWebViewWrapper, "injectJavaScript", it)
        }
    }

    fun launchScanCard(data: JSONObject) {
//        try {
//            val scanCardCallbackClass =
//                Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardCallback")
//            val scanCardManagerClass = Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")
//
//            val callback =
//                java.lang.reflect.Proxy.newProxyInstance(
//                    scanCardCallbackClass.classLoader,
//                    arrayOf(scanCardCallbackClass),
//                ) { _, method, args ->
//                    if (method.name == "onScanResult") {
//                        @Suppress("UNCHECKED_CAST")
//                        val result = args[0] as Map<String, Any?>
//
//                        activity.runOnUiThread {
//                            val jsCode =
//                                """
//                                    window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
//                                    """.trimIndent()
//                            webView.evaluateJavascript(jsCode, null)
//                        }
//                    }
//                    null
//                }
//            val launchMethod =
//                scanCardManagerClass.getMethod(
//                    "launch",
//                    Activity::class.java,
//                    scanCardCallbackClass,
//                )
//            launchMethod.invoke(null, context, callback)
//        } catch (e: Exception) {
//            Log.e("WebViewFragment", "Card scanning not available", e)
//            val result = mapOf("status" to "Failed", "error" to "Card scanning not available")
//            activity.runOnUiThread {
//                val jsCode =
//                    """
//                        window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
//                        """.trimIndent()
//                webView.evaluateJavascript(jsCode, null)
//            }
//        }
    }

    val onMessage = object: Callback {
        override fun invoke(args: Map<String, Any?>) {
            println(args)
            (args["data"] as? String)?.let {
                val jsonObject = JSONObject(it)

                if (jsonObject.has("sdkInitialised")) {
                    sdkInitialised(jsonObject.getJSONObject("sdkInitialised"))
                }

                if (jsonObject.has("exitPaymentSheet")) {
                    exitPaymentSheet(jsonObject.getJSONObject("exitPaymentSheet"))
                }

                if (jsonObject.has("launchGPay")) {
                    launchGPay(jsonObject.getJSONObject("launchGPay"))
                }

                if (jsonObject.has("launchScanCard")) {
                    launchScanCard(jsonObject.getJSONObject("launchScanCard"))
                }
            }
        }
    }


//    @Deprecated("Deprecated in Java")
//    override fun onViewCreated(
//        view: View?,
//        savedInstanceState: Bundle?,
//    ) {
//        super.onViewCreated(view, savedInstanceState)
//        // Set up back press handling after view is created
//        setupBackPressHandling()
//    }

//    private fun setupBackPressHandling() {
//        val keyListener =
//            View.OnKeyListener { _, keyCode, event ->
//                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
//                    return@OnKeyListener handleBackPress()
//                }
//                false
//            }
//
//        // Set key listener on multiple views
//        view?.apply {
//            isFocusableInTouchMode = true
//            requestFocus()
//            setOnKeyListener(keyListener)
//        }
//
//        webViewContainer.apply {
//            isFocusableInTouchMode = true
//            requestFocus()
//            setOnKeyListener(keyListener)
//        }
//
//        mainWebView.apply {
//            isFocusableInTouchMode = true
//            requestFocus()
//            setOnKeyListener(keyListener)
//        }
//
//        webViewContainer.viewTreeObserver
//            .addOnGlobalLayoutListener { possiblyResizeChildOfContent(webViewContainer.layoutParams as FrameLayout.LayoutParams) }
//    }

    /**
     * Handles the back press event.
     * If there are multiple WebViews, it closes the topmost one.
     * If the main WebView can go back, it navigates back.
     * Otherwise, it removes the fragment.
     *
     * @return true if the back press was handled, false otherwise
     */
//    fun handleBackPress(): Boolean {
//        // If there are multiple WebViews (popups), close the topmost one
//        if (webViews.size > 1) {
//            val lastWebView = webViews.last()
//            webViews.remove(lastWebView)
//            webViewContainer.removeView(lastWebView)
//            lastWebView.destroy()
//            return true
//        }
//
//        // If the main WebView can go back, navigate back
//        if (::mainWebView.isInitialized && mainWebView.canGoBack()) {
//            mainWebView.goBack()
//            return true
//        }
//        // Otherwise, remove the fragment (same as MainActivity's onBackPressed)
//        PaymentSheetCallbackManager.executeCallback("{\"status\":\"cancelled\"}")
//        activity.runOnUiThread {
//            mainWebView.loadUrl(bundleUrl)
//        }
//        activity
//            ?.fragmentManager
//            ?.beginTransaction()
//            ?.detach(this)
//            ?.commit()
//        return true
//    }
//
//    private var usableHeightPrevious = 0
//
//    private fun possiblyResizeChildOfContent(frameLayoutParams: FrameLayout.LayoutParams) {
//        val usableHeightNow = computeUsableHeight()
//
//        if (usableHeightNow != usableHeightPrevious) {
//            val usableHeightSansKeyboard = webViewContainer.rootView.height
//            val heightDifference = usableHeightSansKeyboard - usableHeightNow
//            if (heightDifference > (usableHeightSansKeyboard / 4)) {
//                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference
//            } else {
//                val insets = webViewContainer.rootView.rootWindowInsets
//                frameLayoutParams.height = usableHeightSansKeyboard - (insets?.systemWindowInsetBottom ?: 0)
//            }
//            webViewContainer.requestLayout()
//            usableHeightPrevious = usableHeightNow
//        }
//    }
//
//    private fun computeUsableHeight(): Int {
//        val r = Rect()
//        webViewContainer.getWindowVisibleDisplayFrame(r)
//        return r.bottom
//    }

//    /**
//     * Creates and configures a new WebView instance.
//     *
//     * This method sets up the WebView with the necessary settings,
//     * such as enabling JavaScript and setting up a WebChromeClient
//     * to handle new window creation.
//     *
//     * @return The newly created WebView instance.
//     */
//    @SuppressLint("SetJavaScriptEnabled")
//    private fun createWebView(): WebView {
//        return WebView(context).apply {
//            layoutParams =
//                ViewGroup.LayoutParams(
//                    ViewGroup.LayoutParams.MATCH_PARENT,
//                    ViewGroup.LayoutParams.MATCH_PARENT,
//                )
//            setBackgroundColor(Color.TRANSPARENT)
//            settings.apply {
//                javaScriptEnabled = true
//                javaScriptCanOpenWindowsAutomatically = true
//                allowFileAccessFromFileURLs = false
//                allowUniversalAccessFromFileURLs = false
//                allowContentAccess = false
//                allowFileAccess = false
//                setSupportMultipleWindows(true)
//            }
//
//            webViewClient =
//                object : WebViewClient() {
//                    override fun shouldOverrideUrlLoading(
//                        view: WebView?,
//                        request: WebResourceRequest?,
//                    ): Boolean {
//                        view?.loadUrl(request?.url.toString())
//                        return true
//                    }
//
//                    override fun onPageFinished(
//                        view: WebView?,
//                        url: String?,
//                    ) {
//                        super.onPageFinished(view, url)
//                    }
//                }
//
//            webChromeClient =
//                object : WebChromeClient() {
//                    override fun onCreateWindow(
//                        view: WebView?,
//                        dialog: Boolean,
//                        userGesture: Boolean,
//                        resultMsg: Message,
//                    ): Boolean {
//                        val newWebView = createNewWebView()
//                        webViews.add(newWebView)
//                        webViewContainer.addView(newWebView)
//                        val transport = resultMsg.obj as WebView.WebViewTransport
//                        transport.webView = newWebView
//                        resultMsg.sendToTarget()
//                        return true
//                    }
//
//                    override fun onJsAlert(
//                        view: WebView,
//                        url: String,
//                        message: String,
//                        result: JsResult,
//                    ): Boolean {
//                        val dialog =
//                            AlertDialog
//                                .Builder(view.context)
//                                .setTitle("Warning")
//                                .setMessage(message)
//                                .setPositiveButton("OK") { _, _ ->
//                                    result.confirm()
//                                }.create()
//                        dialog.show()
//
//                        return true
//                    }
//                }
//
//            val webAppInterface =
//                if (isScanCardAvailable()) {
//                    WebAppInterfaceWithScanCard(activity, this@WebViewFragment, this, bundleUrl)
//                } else {
//                    WebAppInterfaceWithoutScanCard(activity, this@WebViewFragment, this, bundleUrl)
//                }
//            addJavascriptInterface(webAppInterface, "AndroidInterface")
//            loadUrl(bundleUrl)
//        }
//    }

//    /**
//     * Creates and configures a new WebView instance for a new window.
//     *
//     * This method sets up the WebView with the necessary settings,
//     * such as enabling JavaScript and setting up a WebViewClient
//     * to handle page loading and closing windows.
//     *
//     * @return The newly created WebView instance.
//     */
//    @SuppressLint("SetJavaScriptEnabled")
//    private fun createNewWebView(): WebView =
//        WebView(context).apply {
//            layoutParams =
//                RelativeLayout.LayoutParams(
//                    RelativeLayout.LayoutParams.MATCH_PARENT,
//                    RelativeLayout.LayoutParams.MATCH_PARENT,
//                )
//            settings.apply {
//                javaScriptEnabled = true
//                allowFileAccessFromFileURLs = false
//                allowUniversalAccessFromFileURLs = false
//                allowContentAccess = false
//                allowFileAccess = false
//            }
//
//            webViewClient =
//                object : WebViewClient() {
//                    override fun onPageFinished(
//                        view: WebView?,
//                        url: String?,
//                    ) {
//                        super.onPageFinished(view, url)
//                    }
//                }
//
//            webChromeClient =
//                object : WebChromeClient() {
//                    override fun onCloseWindow(window: WebView) {
//                        webViews.remove(window)
//                        webViewContainer.removeView(window)
//                        window.destroy()
//                    }
//                }
//        }

    /**
     * Sends a request body to the WebView using JavaScript.
     *
     * This method constructs a JavaScript code snippet that sends a message
     * to the WebView containing the request body.
     *
     * @param requestBody The request body to send.
     */
    fun setRequestBody(requestBody: String) {
        val jsCode =
            """window.postMessage('{"initialProps":$requestBody}', '*');""".trimIndent()

        val args = Arguments.createArray()
        args.pushString(jsCode)
        this.requestBody = args

        if(sdkLoaded) {
            hSWebViewManagerImpl.receiveCommand(hSWebViewWrapper, "injectJavaScript", args)
        }
    }

//    @Deprecated("Deprecated in Java")
//    override fun onDestroyView() {
//        hSWebViewWrapper.webView.destroy()
//        super.onDestroyView()
//    }
//
//    @Deprecated("Deprecated in Java")
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        hSWebViewWrapper.webView.saveState(outState)
//    }

    /**
     * Sends a result to the WebView using JavaScript.
     *
     * This method constructs a JavaScript function call that sends a message
     * to the WebView containing the result.
     *
     * @param result The result to send.
     */
    private fun sendResultToWebView(result: Map<String, Any?>) {
        try {
            val javascriptFunction =
                """window.postMessage(JSON.stringify({"googlePayData":  ${JSONObject(result)}}), '*');""".trimIndent()

            val args = Arguments.createArray()
            args.pushString(javascriptFunction)
            hSWebViewManagerImpl.receiveCommand(hSWebViewWrapper, "injectJavaScript", args)
        } catch (e: Exception) {
            Log.e("sendResultToWebView", "Error sending result to WebView", e)
        }
    }

    /**
     * Inner class to define a JavaScript interface for the WebView.
     *
     * This interface provides methods that can be called from JavaScript
     * code running in the WebView.
     *
     * @param context The Activity context.
     */
    open class WebAppInterface(
        val context: Activity,
        val webFragment: Fragment,
        val webView: WebView,
        val bundleUrl: String,
    ) {
        @JavascriptInterface
        fun exitPaymentSheet(data: String) {
            PaymentSheetCallbackManager.executeCallback(data)
            context.runOnUiThread {
                webView.loadUrl(bundleUrl)
            }
            context.fragmentManager
                .beginTransaction()
                .detach(webFragment)
                .commit()
        }

        @JavascriptInterface
        fun launchGPay(data: String) {
            GooglePayCallbackManager.setCallback(
                context,
                data,
                ::sendResultToWebView,
            )
        }

        private fun sendResultToWebView(result: Map<String, Any?>) {
            try {
                val javascriptFunction =
                    """window.postMessage(JSON.stringify({"googlePayData":  ${JSONObject(result)}}), '*');""".trimIndent()
                context.runOnUiThread {
                    webView.evaluateJavascript(javascriptFunction, null)
                }
            } catch (e: Exception) {
                Log.e("sendResultToWebView", "Error sending result to WebView", e)
            }
        }

        @JavascriptInterface
        fun sdkInitialised(data: String) {
            /* activity.runOnUiThread {
                val jsCode = """window.postMessage('{"initialProps":$requestBody}', '*');""".trimIndent()
                mainWebView.evaluateJavascript(jsCode, null)
            } */
        }
    }

    class WebAppInterfaceWithScanCard(
        context: Activity,
        webFragment: Fragment,
        webView: WebView,
        bundleUrl: String,
    ) : WebAppInterface(context, webFragment = webFragment, webView = webView, bundleUrl = bundleUrl) {
        @JavascriptInterface
        fun launchScanCard(data: String) {
            try {
                val scanCardCallbackClass =
                    Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardCallback")
                val scanCardManagerClass = Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")

                val callback =
                    java.lang.reflect.Proxy.newProxyInstance(
                        scanCardCallbackClass.classLoader,
                        arrayOf(scanCardCallbackClass),
                    ) { _, method, args ->
                        if (method.name == "onScanResult") {
                            @Suppress("UNCHECKED_CAST")
                            val result = args[0] as Map<String, Any?>

                            context.runOnUiThread {
                                val jsCode =
                                    """
                                    window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
                                    """.trimIndent()
                                webView.evaluateJavascript(jsCode, null)
                            }
                        }
                        null
                    }
                val launchMethod =
                    scanCardManagerClass.getMethod(
                        "launch",
                        Activity::class.java,
                        scanCardCallbackClass,
                    )
                launchMethod.invoke(null, context, callback)
            } catch (e: Exception) {
                Log.e("WebViewFragment", "Card scanning not available", e)
                val result = mapOf("status" to "Failed", "error" to "Card scanning not available")
                context.runOnUiThread {
                    val jsCode =
                        """
                        window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
                        """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                }
            }
        }
    }

    class WebAppInterfaceWithoutScanCard(
        context: Activity,
        webFragment: Fragment,
        webView: WebView,
        bundleUrl: String,
    ) : WebAppInterface(context, webFragment = webFragment, webView = webView, bundleUrl = bundleUrl)
}
