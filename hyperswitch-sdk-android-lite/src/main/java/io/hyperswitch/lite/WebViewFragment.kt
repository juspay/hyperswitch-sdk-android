package io.hyperswitch.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.graphics.Color
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
import android.widget.RelativeLayout
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import org.json.JSONObject


//const val bundleUrl = "http://192.168.0.103:8080"

//const val bundleUrl: String = "https://dev.hyperswitch.io/mobile/v1/index.html"
//const val bundleUrl: String = "http://10.10.70.164:5252:8080"
//const val bundleUrl: String = "http://192.168.0.115:8080"
const val bundleUrl: String = "http://10.10.30.93:8080"

open class WebViewFragment : Fragment() {
    private fun isScanCardAvailable(): Boolean {
        return try {
            Class.forName("io.hyperswitch.hyperswitchScanCardLite.ScanCardManager")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
}
    private lateinit var webViewContainer: RelativeLayout
    private lateinit var mainWebView: WebView
    private val webViews = mutableListOf<WebView>()


    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainWebView = createWebView()
        webViewContainer = RelativeLayout(context)
        webViews.add(mainWebView)
        webViewContainer.addView(mainWebView)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return webViewContainer
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
        return WebView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            webViewClient = object : WebViewClient()
            {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, dialog: Boolean, userGesture: Boolean, resultMsg: Message
                ): Boolean {
                    val newWebView = createNewWebView()
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            view?.loadUrl(request?.url.toString())
                            return true
                        }
                    }
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
                WebAppInterfaceWithScanCard(activity,this@WebViewFragment,this)
            } else {
            WebAppInterfaceWithoutScanCard(activity,this@WebViewFragment,this)
        }
            addJavascriptInterface(webAppInterface, "AndroidInterface")
            loadUrl(bundleUrl)
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
            settings.javaScriptEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) {
                    webViews.remove(window)
                    webViewContainer.removeView(window)
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

    open class WebAppInterface( val context: Activity,val webFragment: Fragment,val webView:WebView) {
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

    class WebAppInterfaceWithScanCard(context: Activity,webFragment: Fragment,webView:WebView) : WebAppInterface(context, webFragment=webFragment,webView=webView) {
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
                        val jsonResult = JSONObject(result).toString()
                        context.runOnUiThread {
                            val jsCode = """
                        window.postMessage(JSON.stringify({ scanCardData: $jsonResult }), '*');
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
                val jsonResult = JSONObject(result).toString()
                context.runOnUiThread {
                    val jsCode = """
                window.postMessage(JSON.stringify({ scanCardData: $jsonResult }), '*');
            """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                }
            }
        }
    }

    class WebAppInterfaceWithoutScanCard(context: Activity,webFragment: Fragment,webView:WebView) :WebAppInterface(context, webFragment=webFragment,webView=webView)
    }

