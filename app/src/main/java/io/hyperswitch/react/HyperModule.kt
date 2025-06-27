package io.hyperswitch.react

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.view.BasePaymentWidget
import io.hyperswitch.payments.launcher.PaymentMethod
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

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
            widget?.setWidgetHeight(height)
        }
    }

private fun findFirstExpressCheckoutWidget(rootView: View): BasePaymentWidget? {
    if (rootView is BasePaymentWidget && rootView.getPaymentMethod() == "expressCheckout") {
        return rootView
    }
    // Check child views
    if (rootView is ViewGroup) {
        for (i in 0 until rootView.childCount) {
            val childView = rootView.getChildAt(i)
            val result = findFirstExpressCheckoutWidget(childView)
            if (result != null) {
                return result
            }
        }
    }

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
                    callBack.invoke(Arguments.fromBundle(LaunchOptions(it).toBundle(data)))
                },
            )
        } ?: run {
            GooglePayCallbackManager.setCallback(
                reactApplicationContext,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(Arguments.fromBundle(LaunchOptions().toBundle(data)))
                },
            )
        }
    }

    // Method to exit the payment sheet
    @ReactMethod
    fun exitPaymentsheet(rootTag: Int, paymentResult: String, reset: Boolean) {
        val isFragment = PaymentSheetCallbackManager.executeCallback(paymentResult)
        (currentActivity as? FragmentActivity)?.let {
            if (isFragment)
                it.supportFragmentManager.findFragmentByTag("paymentSheet")
                    ?.let { fragment ->
                        it.supportFragmentManager.beginTransaction().hide(fragment)
                            .commitAllowingStateLoss()
                    }
            else
                it.finish()
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
    }

    // Variable to keep track of event listener count
    private var listenerCount = 0

    // Method to add event listener
    @ReactMethod
    fun addListener(eventName: String?) {
        if (listenerCount == 0) {
            HyperEventEmitter.initialize(rct)
        }
        listenerCount += 1
    }

    // Method to remove event listeners
    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount -= count
        if (listenerCount == 0) {
            // Remove upstream listeners, stop unnecessary background task
        }
    }
}
