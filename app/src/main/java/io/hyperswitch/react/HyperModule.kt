package io.hyperswitch.react

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.payments.paymentlauncher.PaymentLauncher
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

class HyperModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    companion object {
        @JvmStatic
        private var reactContext: ReactApplicationContext? = null

        private val pendingEvents = ConcurrentLinkedQueue<Pair<String, Map<String, String?>>>()

        @JvmStatic
        fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
            val writableMap = Arguments.createMap()
            for ((key, value) in map) {
                writableMap.putString(key, value)
            }

            if (reactContext != null && reactContext!!.hasCatalystInstance()) {
                try {
                    reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        ?.emit(tag, writableMap)
                } catch (e: Exception) {
                    pendingEvents.add(Pair(tag, map))
                }
            } else {
                pendingEvents.add(Pair(tag, map))
            }
        }

        @JvmStatic
        fun confirmCardStatic(map: MutableMap<String, String?>) {
            confirmStatic("confirm", map)
        }

        @JvmStatic
        fun confirmECStatic(map: MutableMap<String, String?>) {
            confirmStatic("confirmEC", map)
        }

        @JvmStatic
        private fun processPendingEvents() {
            if (reactContext == null || !reactContext!!.hasCatalystInstance()) {
                return
            }

            val iterator = pendingEvents.iterator()
            while (iterator.hasNext()) {
                val (tag, map) = iterator.next()
                try {
                    val writableMap = Arguments.createMap()
                    for ((key, value) in map) {
                        writableMap.putString(key, value)
                    }

                    reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        ?.emit(tag, writableMap)

                    iterator.remove()
                } catch (e: Exception) {
                    Log.e("Error processing pending event", e.toString())
                }
            }
        }

        @JvmStatic
        fun onContextInitialized() {
            processPendingEvents()
        }
    }

    override fun getName(): String {
        reactContext = rct
        processPendingEvents()
        return "HyperModule"
    }

    // Using invalidate instead of deprecated onCatalystInstanceDestroy
    override fun invalidate() {
        super.invalidate()
        reactContext = null
    }


    @ReactMethod
    fun sendMessageToNative(rnMessage: String) {
        try {
            val jsonObject = JSONObject(rnMessage)

            if (jsonObject.optBoolean("isReady", false)) {
                reactContext = rct
                onContextInitialized()
                val paymentMethodType = jsonObject.optString("paymentMethodType", "")
                when (paymentMethodType) {
                    "google_pay" -> {
                        WidgetLauncher.onGPayPaymentReadyWithUI.onReady(true)
                    }
                    "paypal" -> {
                        WidgetLauncher.onPaypalPaymentReadyWithUI.onReady(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Error processing message", e.toString())
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
        when (widgetType) {
            "google_pay" -> WidgetLauncher.onGPayPaymentResultCallBack(paymentResult)
            "paypal" ->  WidgetLauncher.onPaypalPaymentResultCallBack(paymentResult)
            "expressCheckout" -> {}
        }
    }

    // Method to exit the card form
    @ReactMethod
    fun exitCardForm(paymentResult: String) {
        PaymentLauncher.Companion.onPaymentResultCallBack(paymentResult)
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
            // Set up any upstream listeners or background tasks as necessary
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
