package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.BuildConfig
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.payments.launcher.PaymentMethod
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.webview.utils.Callback as HSCallback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal fun ReadableMap.toExitResultJson(): String {
    val json = JSONObject()
    val iterator = keySetIterator()
    while (iterator.hasNextKey()) {
        val key = iterator.nextKey()
        json.put(key, getString(key))
    }
    return json.toString()
}

/**
 * Stateless JS → native entry point for the shared host. Every call names the
 * surface it comes from by root tag; the owner of that surface is resolved
 * through the mounting layer and handles the call.
 */
class HyperModule internal constructor(
    private val rct: ReactApplicationContext,
    private val runtime: HyperReactRuntime,
) : io.hyperswitch.react.codegen.NativeHyperModuleSpec(rct) {

    private val eventEmitter: HyperEventEmitter get() = runtime.eventEmitter

    companion object {
        // Reached by reflection from the api module's UnififedPaymentLauncher; legacy flows only.
        @Volatile
        private var legacyEmitter: HyperEventEmitter? = null

        @JvmStatic
        fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
            legacyEmitter?.confirm(tag, map)
        }

        @JvmStatic
        fun confirmCardStatic(map: MutableMap<String, String?>) {
            legacyEmitter?.confirmCard(map)
        }

        @JvmStatic
        fun confirmECStatic(map: MutableMap<String, String?>) {
            legacyEmitter?.confirmEC(map)
        }
    }

    override fun initialize() {
        super.initialize()
        eventEmitter.attach(this)
        legacyEmitter = eventEmitter
    }

    // Using invalidate instead of deprecated onCatalystInstanceDestroy
    override fun invalidate() {
        super.invalidate()
        eventEmitter.detach()
    }

    fun emitEvent(tag: String, payload: WritableMap) {
        when (tag) {
            "confirm" -> emitConfirm(payload)
            "widget" -> emitWidget(payload)
            "confirmEC" -> emitConfirmEC(payload)
            else -> Log.w("HyperModule", "emitEvent: unknown event tag $tag")
        }
    }

    override fun launchApplePay(requestObj: String, callback: Callback) {}

    override fun startApplePay(requestObj: String, callback: Callback) {}

    override fun presentApplePay(requestObj: String, callback: Callback) {}

    override fun onAddPaymentMethod(data: String) {}

    override fun exitPaymentMethodManagement(rootTag: Double, result: String, reset: Boolean) {}

    override fun updateWidgetHeight(height: Double) {
        // Express checkout widget height adjustment is not yet implemented.
    }

    /**
     * Called from JS when a wallet confirm button is tapped.
     * Stores the callback; native later calls [resolveConfirmCallback] to proceed/abort.
     */
    override fun onPaymentConfirmButtonClick(rootTag: Double, payload: String, callback: Callback) {
        withFragment(rootTag) { fragment ->
            try {
                if (fragment == null) {
                    callback.invoke(true)
                } else {
                    fragment.notifyConfirmButtonClicked(payload) { proceed: Boolean ->
                        callback.invoke(proceed)
                    }
                }
            } catch (_: Exception) {
                callback.invoke(false)
            }
        }
    }

    // Method to launch Google Pay payment
    override fun launchGPay(requestObj: String, callback: Callback) {
        val googlePayRequest = requestObj
        val callBack = callback
        currentActivity?.let {
            GooglePayCallbackManager.setCallback(
                it,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                it, BuildConfig.VERSION_NAME
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
                                reactApplicationContext, BuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        }
    }

    /**
     * A sheet presented by a session is owned by its fragment, which holds the
     * completion. Anything else (HyperActivity, legacy openReactView) still
     * resolves through the one-shot PaymentSheetCallbackManager.
     */
    override fun exitPaymentsheet(rootTag: Double, result: ReadableMap, reset: Boolean) {
        val paymentResult = result.toExitResultJson()
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            val fragment = owner as? HyperFragment
            if (fragment != null && fragment.hasPaymentResultCallback()) {
                // Removal is queued behind the callback on the main looper, so the merchant
                // sees the result first and the surface stops right after.
                (fragment.activity as? FragmentActivity)?.supportFragmentManager
                    ?.beginTransaction()?.remove(fragment)?.commitAllowingStateLoss()
                fragment.notifyResult(CallbackType.PAYMENT_RESULT, paymentResult)
            } else {
                exitLegacyPaymentsheet(paymentResult)
            }
        }
    }

    private fun exitLegacyPaymentsheet(paymentResult: String) {
        // Dismiss first: the merchant's callback may present again straight away.
        (currentActivity as? FragmentActivity)?.let {
            if (PaymentSheetCallbackManager.isFragmentPresentation()) {
                it.supportFragmentManager.findFragmentByTag("paymentSheet")?.let { fragment ->
                    it.supportFragmentManager.beginTransaction().hide(fragment)
                        .commitAllowingStateLoss()
                }
            } else {
                it.finish()
            }
        }
        PaymentSheetCallbackManager.executeCallback(paymentResult)
    }

    // Method to exit the widget
    override fun exitWidget(result: ReadableMap, widgetType: String) {
        val paymentResult = result.toExitResultJson()
        WidgetLauncher.onPaymentResultCallback(widgetType, paymentResult)
    }

    // Method to exit the card form
    override fun exitCardForm(result: String) {
        val paymentResult = result
        WidgetLauncher.onPaymentResultCallback(PaymentMethod.CARD.apiValue, paymentResult)
    }

    // Method to exit widget payment sheet
    override fun exitWidgetPaymentsheet(rootTag: Double, result: ReadableMap, reset: Boolean) {
        val paymentResult = result.toExitResultJson()
        withFragment(rootTag) {
            it?.notifyResult(CallbackType.PAYMENT_RESULT, paymentResult)
        }
    }

    override fun notifyWidgetPaymentResult(rootTag: Double, result: ReadableMap) {
        val paymentResult = result.toExitResultJson()
        withFragment(rootTag) { fragment ->
            if (fragment == null) {
                Log.w(
                    "HyperModule",
                    "notifyWidgetPaymentResult: no fragment found for rootTag=$rootTag"
                )
            } else {
                fragment.notifyResult(CallbackType.CONFIRM_ACTION, paymentResult)
            }
        }
    }

    override fun onUpdateIntentEvent(rootTag: Double, eventType: String, result: ReadableMap) {
        val json = result.toExitResultJson()
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            when (owner) {
                is UpdateIntentReplyTarget -> owner.onUpdateIntentReply(eventType, json)
                else -> Log.w("HyperModule", "onUpdateIntentEvent: no prefetch owner for rootTag=$rootTag ($eventType)")
            }
        }
    }

    override fun emitPaymentEvent(rootTag: Double, eventType: String, payload: ReadableMap) {
        withFragment(rootTag) { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "emitPaymentEvent: no fragment found for rootTag=$rootTag")
            } else {
                fragment.notifyEvent(eventType, payload)
            }
        }
    }

    override fun openIframeBridge(url: String, timeoutMs: Double, callback: Callback) {
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

    private fun withFragment(rootTag: Double, block: (HyperFragment?) -> Unit) {
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner -> block(owner as? HyperFragment) }
    }
}
