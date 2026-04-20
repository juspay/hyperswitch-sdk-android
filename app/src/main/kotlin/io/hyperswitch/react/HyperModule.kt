package io.hyperswitch.react

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
import io.hyperswitch.BuildConfig
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.view.PaymentWidgetView
import io.hyperswitch.payments.launcher.PaymentMethod
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class HyperModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {
    companion object {
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
        HyperEventEmitter.initialize(rct)
        return "HyperModule"
    }

    // Using invalidate instead of deprecated onCatalystInstanceDestroy
    override fun invalidate() {
        super.invalidate()
        HyperEventEmitter.deinitialize()
    }

    @ReactMethod
    fun updateWidgetHeight(height: Int) {
        val activity = currentActivity ?: return
        activity.runOnUiThread {
            // Find the first ExpressCheckoutWidget instance
            val rootView = activity.findViewById<View>(android.R.id.content)
            val widget = findFirstExpressCheckoutWidget(rootView)

            // Update its height if found
//            widget?.setWidgetHeight(height)
        }
    }

    private fun findFirstExpressCheckoutWidget(rootView: View): PaymentWidgetView? {
//        if (rootView is PaymentWidgetView && rootView.getPaymentMethod() == "expressCheckout") {
//            return rootView
//        }
//        // Check child views
//        if (rootView is ViewGroup) {
//            for (i in 0 until rootView.childCount) {
//                val childView = rootView.getChildAt(i)
//                val result = findFirstExpressCheckoutWidget(childView)
//                if (result != null) {
//                    return result
//                }
//            }
//        }

        // Not found in this branch
        return null
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
    fun exitWidgetPaymentsheet(rootTag: Int, paymentResult: String, reset: Boolean) {
        findFragmentWithRootTag(rootTag.toInt(), {
            it?.notifyResult(CallbackType.PAYMENT_RESULT, paymentResult)
        })
    }

    @ReactMethod
    fun notifyWidgetPaymentResult(rootTag: Int, result: String) {
        try {
            findFragmentWithRootTag(rootTag, {
                it?.notifyResult(CallbackType.CONFIRM_ACTION, result)
            })
        } catch (_: Exception) {
//      Log.i("HyperModule", "Error in notifyWidgetPaymentResult")
        }
    }
    @ReactMethod
    fun onUpdateIntentEvent(rootTag: Int, type: String, result: String) {
        try {
            findFragmentWithRootTag(rootTag, {
                if (type == "UPDATE_INTENT_INIT_RETURNED") {
                    it?.notifyResult(
                        CallbackType.UPDATE_INTENT_INIT,
                        result
                    )
                } else if (type == "UPDATE_INTENT_COMPLETE_RETURNED") {
                    it?.notifyResult(
                        CallbackType.UPDATE_INTENT_COMPLETE,
                        result
                    )
                }
            })
        } catch (_: Exception) {

        }
    }

    // Variable to keep track of event listener count
    private val listenerCount = AtomicInteger(0)


    // Method to add event listener
    @ReactMethod
    fun addListener(eventName: String?) {
        if (listenerCount.get() == 0) {
            HyperEventEmitter.initialize(rct)
        }
        listenerCount.set(listenerCount.get() +  1)
    }

    // Method to remove event listeners
    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount.set(listenerCount.get() - count)
        if (listenerCount.get()  == 0) {
            // Remove upstream listeners, stop unnecessary background task
        }
    }

    @ReactMethod
    fun emitPaymentEvent(rootTag: Int, eventType: String, payload: ReadableMap) {
        try {
//            if (rootTag <= 0) {
//                HyperswitchRNWrapperNativeModule.emitPaymentSheetEvent(eventType, payload)
//            } else {
                findFragmentWithRootTag(rootTag, {
                    it?.notifyEvent(eventType, payload)
                })
//            }
        } catch (_: Exception) {
        }
    }

    private fun findFragmentWithRootTag(rootTag: Int, onFound: (HyperFragment?) -> Unit) {
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
