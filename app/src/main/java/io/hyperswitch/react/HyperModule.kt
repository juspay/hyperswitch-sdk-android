package io.hyperswitch.react

import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.hyperswitch.PaymentSession
import io.hyperswitch.payments.googlepaylauncher.GooglePayCallbackManager
import io.hyperswitch.payments.paymentlauncher.PaymentLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.threedslibrary.service.Result
import io.hyperswitch.threedslibrary.service.Result.Failure
import io.hyperswitch.threedslibrary.service.Result.Success
import org.json.JSONObject

class HyperModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    companion object {
        @JvmStatic
        var reactContext: ReactApplicationContext? = null

        @JvmStatic
        var reactContextCard: ReactApplicationContext? = null

        @JvmStatic
        var reactContextEC: ReactApplicationContext? = null
        private var pendingEmitList: ArrayList<Pair<String, WritableMap>> = ArrayList()

        // Method to emit 'confirm' event for card
        fun confirmCard(map: WritableMap) {
            if (reactContextCard != null && reactContextCard!!.hasCatalystInstance())
                reactContextCard!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit("confirm", map)
            else
                pendingEmitList.add(Pair("confirm", map))
        }

        // Method to emit 'confirmEC' event for express checkout
        fun confirmEC(map: WritableMap) {
            if (reactContextEC != null && reactContextEC!!.hasCatalystInstance())
                reactContextEC!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit("confirmEC", map)
            else
                pendingEmitList.add(Pair("confirmEC", map))
        }

        // Generic method to emit events
        fun confirm(tag: String, map: WritableMap) {
            if (reactContext != null && reactContext!!.hasCatalystInstance())
                reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(tag, map)
            else
                pendingEmitList.add(Pair(tag, map))
        }

        // Method to emit pending events once context is initialized
        fun onContextInitialized() {
            pendingEmitList.forEach { pendingEmit ->
                pendingEmit.let { (tag, map) ->
                    confirm(tag, map)
                }
            }
            pendingEmitList = ArrayList()
            reactContext = null
        }
    }

    // Set the name for this module
    override fun getName(): String {
        reactContext = rct
        reactContextCard = rct
        reactContextEC = rct
        return "HyperModule"
    }

    // Method to handle messages from React Native
    @ReactMethod
    fun sendMessageToNative(rnMessage: String) {
        val jsonObject = JSONObject(rnMessage)
        if (jsonObject.getBoolean("isReady")) {
            reactContext = rct
            onContextInitialized()
            when (jsonObject.getString("paymentMethodType")) {
                "google_pay" -> {}
                "paypal" -> {}
            }
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
            "google_pay" -> {}
            "paypal" -> {}
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

    @ReactMethod
    fun launch3DS(request: String, callBack: Callback) {
        currentActivity?.let {
            val jsonObject = JSONObject(request)
            val threeDsData = jsonObject.getJSONObject("threeDsData")

            val ps = PaymentSession(it, jsonObject.getString("publishableKey"))
            val session = ps.initAuthenticationSession(
                jsonObject.getString("clientSecret"),
                jsonObject.getString("merchantId"),
                threeDsData.getString("directoryServerId"),
                threeDsData.getString("messageVersion"),
            ) { result ->

            }

            session.startAuthentication(it) { result ->
                when (result) {
                    is Success -> {
                        val map = Arguments.createMap()
                        map.putString("status", "succeeded")
                        map.putString("message", result.message)
                        callBack.invoke(map)
                    }
                    is Failure -> {
                        val map = Arguments.createMap()
                        map.putString("status", "failed")
                        map.putString("message", result.errorMessage)
                        callBack.invoke(map)
                    }
                }
            }
        }
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
