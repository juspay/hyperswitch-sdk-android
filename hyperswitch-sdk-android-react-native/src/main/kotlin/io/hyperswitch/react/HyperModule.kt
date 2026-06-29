package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.IllegalViewOperationException
import com.facebook.react.uimanager.UIManagerModule
import io.hyperswitch.core.BuildConfig as CoreBuildConfig
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.payments.launcher.PaymentMethod
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.webview.utils.Callback as HSCallback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class HyperModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    companion object {
        val NAME = "HyperModule"
        // Static methods with unique signatures for reflection access from lite SDK
        @JvmStatic
        fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmStatic(tag, map)
        }

        @JvmStatic
        fun confirmCardStatic(map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmCardStatic(map)
        }

        @JvmStatic
        fun confirmECStatic(map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmECStatic(map)
        }
    }

    override fun getName(): String {
        HyperEventEmitter.initialize(reactContext)
        return NAME
    }

    // Using invalidate instead of deprecated onCatalystInstanceDestroy
    override fun invalidate() {
        super.invalidate()
        HyperEventEmitter.deinitialize()
    }

    @ReactMethod
    fun updateWidgetHeight(height: Int) {
        // Express checkout widget height adjustment is not yet implemented.
    }

    /**
     * Called from JS when a wallet confirm button is tapped.
     * Stores the callback; native later calls [resolveConfirmCallback] to proceed/abort.
     */
    @ReactMethod
    fun onPaymentConfirmButtonClick(rootTag: Double, payload: String, callback: Callback) {
        findViewWithRootTag(rootTag.toInt()) {
            try {
                if(it == null){
                    callback.invoke(true)
                }else {
                    it.notifyConfirmButtonClicked(payload, { it: Boolean ->
                        callback.invoke(it)
                    })
                }
            } catch (_: Exception) {
                callback.invoke(false)
            }
        }
    }

    @ReactMethod
    fun sendMessageToNative(rnMessage: String) {
        val jsonObject = JSONObject(rnMessage)
        if (jsonObject.optBoolean("isReady", false)) {
//            HyperEventEmitter.initialize(rct)
            WidgetLauncher.onPaymentReadyCallback(true)
        }
    }

    // Method to launch Google Pay payment
    @ReactMethod
    fun launchGPay(googlePayRequest: String, callBack: Callback) {
        currentActivity?.let {
            GooglePayCallbackManager.setCallback(
                it,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                it, CoreBuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        } ?: run {
            GooglePayCallbackManager.setCallback(
                reactApplicationContext,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                reactApplicationContext, CoreBuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        }
    }

    // Method to exit the payment sheet
    @ReactMethod
    fun exitPaymentsheet(rootTag: Int, paymentResult: String, reset: Boolean) {
        val isFragment = PaymentSheetCallbackManager.executeCallback(paymentResult)
        (currentActivity as? FragmentActivity)?.let {
            if (isFragment) it.supportFragmentManager.findFragmentByTag("paymentSheet")
                ?.let { fragment ->
                    it.supportFragmentManager.beginTransaction().hide(fragment)
                        .commitAllowingStateLoss()
                }
            else it.finish()
        }
    }

    // Method to exit the widget
    @ReactMethod
    fun exitWidget(paymentResult: String, widgetType: String) {
        WidgetLauncher.onPaymentResultCallback(widgetType, paymentResult)
    }

    // Method to exit the card form
    @ReactMethod
    fun exitCardForm(paymentResult: String) {
        WidgetLauncher.onPaymentResultCallback(PaymentMethod.CARD.apiValue, paymentResult)
    }

    // Method to launch widget payment sheet
    @ReactMethod
    fun launchWidgetPaymentSheet(paymentResult: String, callBack: Callback) {
    }

    // Method to exit widget payment sheet
    @ReactMethod
    fun exitWidgetPaymentsheet(rootTag: Double, paymentResult: String, reset: Boolean) {
        findViewWithRootTag(rootTag.toInt(), {
            it?.notifyResult(CallbackType.PAYMENT_RESULT, paymentResult)
        })
    }

    @ReactMethod
    fun notifyWidgetPaymentResult(rootTag: Double, result: String) {
        findViewWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w(
                    "HyperModule",
                    "notifyWidgetPaymentResult: no fragment found for rootTag=$rootTag"
                )
            } else {
                fragment.notifyResult(CallbackType.CONFIRM_ACTION, result)
            }
        })
    }

    @ReactMethod
    fun onUpdateIntentEvent(rootTag: Double, type: String, result: String) {
        findViewWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "onUpdateIntentEvent: no fragment found for rootTag=$rootTag")
                return@findViewWithRootTag
            }
            if (type == "UPDATE_INTENT_INIT_RETURNED") {
                fragment.notifyResult(CallbackType.UPDATE_INTENT_INIT, result)
            } else if (type == "UPDATE_INTENT_COMPLETE_RETURNED") {
                fragment.notifyResult(CallbackType.UPDATE_INTENT_COMPLETE, result)
            }
        })
    }

    // Variable to keep track of event listener count
    private val listenerCount = AtomicInteger(0)


    // Method to add event listener
    @ReactMethod
    fun addListener(eventName: String?) {
        if (listenerCount.incrementAndGet() == 1) {
            HyperEventEmitter.initialize(reactContext)
        }
    }

    // Method to remove event listeners
    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount.addAndGet(-count)
    }

    @ReactMethod
    fun emitPaymentEvent(rootTag: Double, eventType: String, payload: ReadableMap) {
        findViewWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "emitPaymentEvent: no fragment found for rootTag=$rootTag")
            } else {
                fragment.notifyEvent(eventType, payload)
            }
        })
    }

    @ReactMethod
   fun openIframeBridge(url: String, timeoutMs: Int, callback: Callback) {
         if (timeoutMs <= 0) {
             callback.invoke("")
             return
         }
         if (url.isBlank()) {
            callback.invoke("")
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val callbackInvoked = AtomicBoolean(false)
        var webViewWrapper: HSWebViewWrapper? = null
        var timeoutRunnable: Runnable? = null

        val invokeCallback = { redirectUrl: String ->
            if (callbackInvoked.compareAndSet(false, true)) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                mainHandler.post {
                    webViewWrapper?.let { wrapper ->
                        try {
                            (wrapper.parent as? ViewGroup)?.removeView(wrapper)
                            wrapper.webView.stopLoading()
                            wrapper.webView.destroy()
                        } catch (e: Exception) {
                            Log.e("HyperDDC", "cleanup error: ${e.message}")
                        }
                    }
                    webViewWrapper = null
                }
                callback.invoke(redirectUrl)
            }
        }

        mainHandler.post {
            val activity = currentActivity ?: run {
                invokeCallback("")
                return@post
            }

            val manager = HSWebViewManagerImpl(activity, HSCallback { _ -> })

            var wrapper: HSWebViewWrapper? = null
            repeat(2) { attempt ->
                if (wrapper != null) return@repeat
                try {
                    wrapper = manager.createViewInstance()
                } catch (e: Exception) {
                    if (attempt == 0) Thread.sleep(200)
                }
            }
            val resolvedWrapper = wrapper ?: run {
                invokeCallback("")
                return@post
            }

            manager.setJavaScriptEnabled(resolvedWrapper, true)

            val ddcBridge = object : Any() {
                @android.webkit.JavascriptInterface
                fun onMessage(data: String) {
                    invokeCallback(data)
                }
            }
            resolvedWrapper.webView.addJavascriptInterface(ddcBridge, "HyperDDCBridge")

//            resolvedWrapper.webView.webViewClient = object : WebViewClient() {
//                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
//                    if (request.isForMainFrame) {
//                        val url = request.url.toString()
//                        Log.d("HyperDDC", "shouldOverride intercepted: $url")
//                        invokeCallback("{\"next_action\":{\"type\":\"redirect_to_url\",\"url\":\"$url\"}}")
//                        return true
//                    }
//                    return false
//                }
//
//                @Suppress("DEPRECATION")
//                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                    Log.d("HyperDDC", "shouldOverride intercepted (legacy): $url")
//                    invokeCallback("{\"next_action\":{\"type\":\"redirect_to_url\",\"url\":\"$url\"}}")
//                    return true
//                }
//            }

            resolvedWrapper.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(1, 1)
                translationX = -9999f
                translationY = -9999f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }

            activity.findViewById<ViewGroup>(android.R.id.content).addView(resolvedWrapper)
            webViewWrapper = resolvedWrapper

            val wrapperHtml = """
                <html><body>
                <iframe src="$url" style="display:none;width:1px;height:1px;"></iframe>
                <script>
                window.addEventListener('message', function(event) {
                  var str = typeof event.data === 'string' ? event.data : JSON.stringify(event.data);
                  try { HyperDDCBridge.onMessage(str); } catch(e) {}
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

    private fun findViewWithRootTag(rootTag: Int, onFound: (HyperFragment?) -> Unit) {
        val uiManagerModule =
            reactApplicationContext.getNativeModule<UIManagerModule?>(UIManagerModule::class.java)

        if (uiManagerModule == null) {
            onFound(null)
            return
        }

        uiManagerModule.addUIBlock { nvhm ->
            try {
                val reactRootView = nvhm.resolveView(rootTag)
                onFound(FragmentManager.findFragment(reactRootView))
            } catch (e: IllegalViewOperationException) {
                onFound(null)
            } catch (e: Exception) {
                onFound(null)
            }
        }
    }
}
