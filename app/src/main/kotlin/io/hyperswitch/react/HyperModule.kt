package io.hyperswitch.react

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.BuildConfig
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.payments.launcher.PaymentMethod
import io.hyperswitch.payments.upilauncher.UpiAppDetector
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.view.BasePaymentWidget
import org.json.JSONObject

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


    @ReactMethod
    fun getInstalledUpiApps(knownAppsJson: String?, promise: Promise) {
        try {
            Log.d("HyperModule", "[UPI] Getting installed UPI apps (knownAppsJson: ${knownAppsJson ?: "null"})")

            val upiApps = UpiAppDetector.getInstalledUpiApps(reactApplicationContext)
            val resultArray: WritableArray = Arguments.createArray()
            
            upiApps.forEach { upiApp ->
                val appMap: WritableMap = Arguments.createMap()
                appMap.putString("packageName", upiApp.packageName)
                appMap.putString("appName", upiApp.appName)
                resultArray.pushMap(appMap)
            }
            
            Log.d("HyperModule", "[UPI] Found ${upiApps.size} UPI apps")
            promise.resolve(resultArray)
        } catch (e: Exception) {
            Log.e("HyperModule", "[UPI] ERROR detecting apps: ${e.message}", e)
            promise.reject("UPI_DETECTION_ERROR", "Failed to detect UPI apps: ${e.message}", e)
        }
    }

    @ReactMethod
    fun openUpiApp(packageName: String?, upiUri: String?, promise: Promise) {
        try {
            Log.d("HyperModule", "[UPI] ========== Opening UPI App ==========")
            Log.d("HyperModule", "[UPI] Package: ${packageName ?: "null (not needed on iOS)"}")
            Log.d("HyperModule", "[UPI] URI: $upiUri")
            
            if (upiUri == null) {
                Log.e("HyperModule", "[UPI] ERROR: UPI URI is null")
                promise.reject("INVALID_URI", "UPI URI cannot be null")
                return
            }
            
            val activity = currentActivity
            if (activity == null) {
                Log.e("HyperModule", "[UPI] ERROR: Current activity is null")
                promise.reject("NO_ACTIVITY", "Activity not available")
                return
            }
            
            Log.d("HyperModule", "[UPI] Current activity: ${activity.javaClass.simpleName}")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                packageName?.let { setPackage(it) }
                data = Uri.parse(upiUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val packageManager = activity.packageManager
            val resolvedActivity = intent.resolveActivity(packageManager)
            
            if (resolvedActivity != null) {
                Log.d("HyperModule", "[UPI] Intent resolved to: ${resolvedActivity.className}")
                Log.d("HyperModule", "[UPI] Launching UPI app...")
                activity.startActivity(intent)
                Log.d("HyperModule", "[UPI] startActivity() called successfully")
                promise.resolve(true)
            } else {
                Log.e("HyperModule", "[UPI] ERROR: No app found to handle intent")
                Log.e("HyperModule", "[UPI] Package '$packageName' may not be installed or cannot handle this URI")
                promise.resolve(false)
            }
        } catch (e: Exception) {
            Log.e("HyperModule", "[UPI] ERROR: Exception occurred - ${e.message}", e)
            e.printStackTrace()
            promise.reject("UPI_APP_ERROR", e.message, e)
        }
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
