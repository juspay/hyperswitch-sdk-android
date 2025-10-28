package io.hyperswitch.click_to_pay

import android.app.Activity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import io.hyperswitch.webview.utils.Arguments
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import org.json.JSONObject

class DefaultClickToPaySessionLauncher(activity: Activity): ClickToPaySessionLauncher {
    private var webView: WebView? = null
    private var parentView: FrameLayout? = null

    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper

    fun initialise(activity: Activity) {
        parentView = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
//            layoutParams = FrameLayout.LayoutParams(1, 1)
//            visibility = View.GONE
        }

        hSWebViewManagerImpl = HSWebViewManagerImpl(activity, onMessage)
        hSWebViewWrapper = hSWebViewManagerImpl.createViewInstance()

        hSWebViewManagerImpl.setJavaScriptEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setMessagingEnabled(hSWebViewWrapper, true)
        hSWebViewManagerImpl.setScalesPageToFit(hSWebViewWrapper, true)

        loadUrl()
    }

    private fun loadUrl() {
        val map = Arguments.createMap()
        map.putString("uri", "https://www.google.com")
        hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
    }

    val onMessage = object: Callback {
        override fun invoke(args: Map<String, Any?>) {
            println(args)
            (args["data"] as? String)?.let {
                val jsonObject = JSONObject(it)

                if (jsonObject.has("sdkInitialised")) {
                    println(jsonObject.getJSONObject("sdkInitialised"))
                }
            }
        }
    }
}