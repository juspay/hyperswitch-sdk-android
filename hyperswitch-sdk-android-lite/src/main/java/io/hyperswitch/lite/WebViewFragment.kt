package io.hyperswitch.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RelativeLayout
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import org.json.JSONObject

open class WebViewFragment : Fragment() {
    private fun isScanCardAvailable(): Boolean {
        return try {
            Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private lateinit var webViewContainer: FrameLayout
    private lateinit var mainWebView: WebView
    private val webViews = mutableListOf<WebView>()

    //URL
    private lateinit var bundleUrl:String

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bundleUrl = getString(R.string.webViewUrl)
        mainWebView = createWebView()
        webViewContainer = FrameLayout(context)
        webViews.add(mainWebView)
        webViewContainer.addView(mainWebView)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return webViewContainer
    }

    @Deprecated("Deprecated in Java")
    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up back press handling after view is created
        setupBackPressHandling()
    }

    private fun setupBackPressHandling() {
        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                return@OnKeyListener handleBackPress()
            }
            false
        }

        // Set key listener on multiple views
        view?.apply {
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener(keyListener)
        }
        
        webViewContainer.apply {
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener(keyListener)
        }
        
        mainWebView.apply {
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener(keyListener)
        }

        webViewContainer.viewTreeObserver
            .addOnGlobalLayoutListener { possiblyResizeChildOfContent(webViewContainer.layoutParams as FrameLayout.LayoutParams) }
    }

    /**
     * Handles the back press event.
     * If there are multiple WebViews, it closes the topmost one.
     * If the main WebView can go back, it navigates back.
     * Otherwise, it removes the fragment.
     *
     * @return true if the back press was handled, false otherwise
     */
    fun handleBackPress(): Boolean {
        // If there are multiple WebViews (popups), close the topmost one
        if (webViews.size > 1) {
            val lastWebView = webViews.last()
            webViews.remove(lastWebView)
            webViewContainer.removeView(lastWebView)
            lastWebView.destroy()
            return true
        }
        
        // If the main WebView can go back, navigate back
        if (::mainWebView.isInitialized && mainWebView.canGoBack()) {
            mainWebView.goBack()
            return true
        }
        // Otherwise, remove the fragment (same as MainActivity's onBackPressed)
        PaymentSheetCallbackManager.executeCallback("{\"status\":\"cancelled\"}")
        activity.runOnUiThread {
            mainWebView.loadUrl(bundleUrl)
        }
        activity?.fragmentManager?.beginTransaction()?.detach(this)?.commit()
        return true
    }
    
    private var usableHeightPrevious = 0
    private fun possiblyResizeChildOfContent(frameLayoutParams: FrameLayout.LayoutParams) {
        val usableHeightNow = computeUsableHeight()

        if (usableHeightNow != usableHeightPrevious) {
            val usableHeightSansKeyboard = webViewContainer.rootView.height
            val heightDifference = usableHeightSansKeyboard - usableHeightNow
            if (heightDifference > (usableHeightSansKeyboard / 4)) {
                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference
            } else {
                val insets = webViewContainer.rootView.rootWindowInsets
                frameLayoutParams.height = usableHeightSansKeyboard - (insets?.systemWindowInsetBottom ?: 0)
            }
            webViewContainer.requestLayout()
            usableHeightPrevious = usableHeightNow
        }
    }

    private fun computeUsableHeight(): Int {
        val r = Rect()
        webViewContainer.getWindowVisibleDisplayFrame(r)
        return r.bottom
    }
    /**
     * Creates and configures a new WebView instance.
     *
     * This method sets up the WebView with the necessary settings,
     * such as enabling JavaScript and setting up a WebChromeClient
     * to handle new window creation.
     *
     * @return The newly created WebView instance.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return try {
            WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                allowContentAccess = false
                allowFileAccess = false
                setSupportMultipleWindows(true)
            }

            webViewClient = object : WebViewClient()
            {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Re-establish focus and key listeners when page finishes
                    // activity?.runOnUiThread {
                    //     setupBackPressHandling()
                    //     mainWebView.requestFocus()
                    // }
                }
            }

            webChromeClient = object : WebChromeClient() {
                // override fun onProgressChanged(view: WebView?, newProgress: Int) {
                //     super.onProgressChanged(view, newProgress)
                //     if (newProgress == 100) {
                //         val url = view?.url
                //         Log.d("WebViewFragment", "Page loaded 100%: $url")
                //         // Check if this is the problematic white screen
                //         if (url != null && url.contains("stripe.com")) {
                //             Log.d("WebViewFragment", "Stripe page detected, re-establishing focus")
                //             activity?.runOnUiThread {
                //                 // Force focus back to our views
                //                 mainWebView.requestFocus()
                //                 webViewContainer.requestFocus()
                //                 view?.requestFocus()
                //             }
                //         }
                //     }
                // }

                override fun onCreateWindow(
                    view: WebView?, dialog: Boolean, userGesture: Boolean, resultMsg: Message
                ): Boolean {
                    Log.d("WebViewFragment", "Creating new window")
                    val newWebView = createNewWebView()
                    webViews.add(newWebView)
                    webViewContainer.addView(newWebView)
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onJsAlert(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {
                    val dialog = AlertDialog.Builder(view.context)
                        .setTitle("Warning")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ ->
                            result.confirm()
                        }
                        .create()
                    dialog.show()

                    return true
                }
            }

           val webAppInterface = if (isScanCardAvailable()) {
            WebAppInterfaceWithScanCard(activity,this@WebViewFragment,this,bundleUrl)
        } else {
        WebAppInterfaceWithoutScanCard(activity,this@WebViewFragment,this,bundleUrl)
    }
                addJavascriptInterface(webAppInterface, "AndroidInterface")
                loadUrl(bundleUrl)
            }
        } catch (e: Exception) {
            Log.e("WebViewFragment", "Failed to create WebView", e)
            // Re-throw the exception to maintain existing error handling behavior
            throw e
        }
    }

    /**
     * Creates and configures a new WebView instance for a new window.
     *
     * This method sets up the WebView with the necessary settings,
     * such as enabling JavaScript and setting up a WebViewClient
     * to handle page loading and closing windows.
     *
     * @return The newly created WebView instance.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewWebView(): WebView {
        return WebView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                allowContentAccess = false
                allowFileAccess = false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) {
                    webViews.remove(window)
                    webViewContainer.removeView(window)
                    window.destroy()
                }
            }
        }
    }

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
        println(jsCode)
        mainWebView.evaluateJavascript(jsCode, null)
    }

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

            activity.runOnUiThread {
                mainWebView.evaluateJavascript(javascriptFunction, null)
            }
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
    open class WebAppInterface( val context: Activity,val webFragment: Fragment,val webView:WebView,val bundleUrl:String) {
        @JavascriptInterface
        fun exitPaymentSheet(data: String) {
            PaymentSheetCallbackManager.executeCallback(data)
            context.runOnUiThread {
                webView.loadUrl(bundleUrl)
            }
            context.fragmentManager.beginTransaction().detach(webFragment).commit()
        }
        @JavascriptInterface
        fun launchGPay(data: String) {
            GooglePayCallbackManager.setCallback(
                context,
                data,
                ::sendResultToWebView
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

    class WebAppInterfaceWithScanCard(context: Activity,webFragment: Fragment,webView:WebView,bundleUrl: String) : WebAppInterface(context, webFragment=webFragment,webView=webView,bundleUrl=bundleUrl) {
        @JavascriptInterface
        fun launchScanCard(data: String) {
            try {
                val scanCardCallbackClass =
                    Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardCallback")
                val scanCardManagerClass = Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")

                val callback = java.lang.reflect.Proxy.newProxyInstance(
                    scanCardCallbackClass.classLoader,
                    arrayOf(scanCardCallbackClass)
                ) { _, method, args ->
                    if (method.name == "onScanResult") {
                        @Suppress("UNCHECKED_CAST")
                        val result = args[0] as Map<String, Any?>

                        context.runOnUiThread {
                            val jsCode = """
                        window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
                    """.trimIndent()
                            webView.evaluateJavascript(jsCode, null)
                        }
                    }
                    null
                }
                val launchMethod = scanCardManagerClass.getMethod(
                    "launch",
                    Activity::class.java,
                    scanCardCallbackClass
                )
                launchMethod.invoke(null, context, callback)

            } catch (e: Exception) {
                Log.e("WebViewFragment", "Card scanning not available", e)
                val result = mapOf("status" to "Failed", "error" to "Card scanning not available")
                context.runOnUiThread {
                    val jsCode = """
                window.postMessage(JSON.stringify({ scanCardData: ${JSONObject(result)} }), '*');
            """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                }
            }
        }
    }

    class WebAppInterfaceWithoutScanCard(context: Activity,webFragment: Fragment,webView:WebView,bundleUrl: String) :WebAppInterface(context, webFragment=webFragment,webView=webView,bundleUrl=bundleUrl)
    }
