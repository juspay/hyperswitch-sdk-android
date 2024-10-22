package io.hyperswitch.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RelativeLayout
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import org.json.JSONObject

open class WebViewFragment : Fragment() {
    private lateinit var webViewContainer: RelativeLayout
    private lateinit var mainWebView: WebView
    private val webViews = mutableListOf<WebView>()
    private val bundleUrl: String = "https://dev.hyperswitch.io/mobile/v1/index.html"

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

//            webViewClient = object : WebViewClient()
//            {
//                override fun onPageFinished(view: WebView?, url: String?) {
//                    super.onPageFinished(view, url)
//                }
//            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, dialog: Boolean, userGesture: Boolean, resultMsg: Message
                ): Boolean {
                    val newWebView = createNewWebView()
                    webViews.add(newWebView)
                    webViewContainer.addView(newWebView)
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }
            }
            addJavascriptInterface(WebAppInterface(activity), "AndroidInterface")
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
    private inner class WebAppInterface(private val context: Activity) {
        @JavascriptInterface
        fun exitPaymentSheet(data: String) {
            PaymentSheetCallbackManager.executeCallback(data)
            activity.runOnUiThread {
                mainWebView.loadUrl(bundleUrl)
            }
            context.fragmentManager.beginTransaction().detach(this@WebViewFragment).commit()
        }

        @JavascriptInterface
        fun launchGPay(data: String) {
            GooglePayCallbackManager.setCallback(
                activity,
                data,
                ::sendResultToWebView
            )
        }

        @JavascriptInterface
        fun sdkInitialised(data: String) {
            /* activity.runOnUiThread {
                val jsCode = """window.postMessage('{"initialProps":$requestBody}', '*');""".trimIndent()
                mainWebView.evaluateJavascript(jsCode, null)
            } */
        }
    }
}

