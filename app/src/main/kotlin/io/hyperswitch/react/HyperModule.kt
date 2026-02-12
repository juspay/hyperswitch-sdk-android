package io.hyperswitch.react

import io.hyperswitch.NativeHyperModuleSpec

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager

/**
 * HyperModules TurboModule implementation that bridges the bundle's expectations
 */
class HyperswitchSdkNativeModule(private val reactContext: ReactApplicationContext) :
    NativeHyperModuleSpec(reactContext) {

    override fun getName(): String {
        return NAME
    }

    override fun sendMessageToNative(message: String) {
        Log.d(NAME, "sendMessageToNative called with: $message")
    }

    override fun launchApplePay(requestObj: String, callback: Callback) {
        callback.invoke("Apple Pay not implemented")
    }


    override fun launchGPay(requestObj: String, callback: Callback) {
        try {
            currentActivity?.let {
                GooglePayCallbackManager.setCallback(
                    it,
                    requestObj,
                    fun(data: Map<String, Any?>) {
                        callback.invoke(mapToWritableMap(data))
                    },
                )
            } ?: run {
                GooglePayCallbackManager.setCallback(
                    reactContext,
                    requestObj,
                    fun(data: Map<String, Any?>) {
                        callback.invoke(mapToWritableMap(data))
                    },
                )
            }
        }catch(e: Exception){
            Log.i("HyperModule", "Failed to launch google pay ${e.message.toString()}")
        }
    }

    override fun exitPaymentsheet(rootTag: Double, result: String, reset: Boolean) {
        val isFragment = PaymentSheetCallbackManager.executeCallback(result)
        (reactApplicationContext.getCurrentActivity()  as? FragmentActivity)?.let {
            if (isFragment) it.supportFragmentManager.findFragmentByTag("paymentSheet")
                ?.let { fragment ->
                    it.supportFragmentManager.beginTransaction().hide(fragment)
                        .commitAllowingStateLoss()
                } else it.finish()
        }
//        try {
//            resolvePromise(result)
//            resetView()
//        } catch (e: JSONException) {
//            // Log.e(NAME, "Failed to parse JSON result: $result", e)
//            resolvePromise(result)
//        }

    }

    override fun exitPaymentMethodManagement(rootTag: Double, result: String, reset: Boolean) {
//    Log.d(NAME, "exitPaymentMethodManagement called $result")
//        try {
//            resolvePromise(result)
//            resetView()
//        } catch (e: JSONException) {
//            // Log.e(NAME, "Failed to parse JSON result: $result", e)
//            resolvePromise(result)
//        }

        // Implementation for exiting payment method management
    }

    override fun exitWidget(result: String, widgetType: String) {
//    Log.d(NAME, "exitWidget called with result: $result, widgetType: $widgetType")
//        try {
//            resolvePromise(result)
//            resetView()
//        } catch (e: JSONException) {
//            // Log.e(NAME, "Failed to parse JSON result: $result", e)
//            resolvePromise(result)
//        }


        // Implementation for exiting widget
    }

    override fun exitCardForm(result: String) {
//    Log.d(NAME, "exitCardForm called with result: $result")
//        try {
//            resolvePromise(result)
//            resetView()
//        } catch (e: JSONException) {
//            // Log.e(NAME, "Failed to parse JSON result: $result", e)
//            resolvePromise(result)
//        }

        // Implementation for exiting card form
    }

    override fun exitWidgetPaymentsheet(rootTag: Double, result: String, reset: Boolean) {
//    Log.d(NAME, "exitWidgetPaymentsheet called")
//        try {
//            resolvePromise(result)
//            resetView()
//        } catch (e: JSONException) {
//            // Log.e(NAME, "Failed to parse JSON result: $result", e)
//            resolvePromise(result)
//        }

        // Implementation for exiting widget payment sheet
    }

    override fun launchWidgetPaymentSheet(requestObj: String, callback: Callback) {
//    Log.d(NAME, "launchWidgetPaymentSheet called")
        // Implementation for launching widget payment sheet
        callback.invoke("Widget payment sheet not implemented")
    }

    override fun updateWidgetHeight(height: Double) {
    Log.d(NAME, "updateWidgetHeight called with height: $height")
        // Implementation for updating widget height
    }

    override fun onAddPaymentMethod(data: String) {
    Log.d(NAME, "onAddPaymentMethod called with data: $data")
        // Implementation for adding payment method
    }

    private fun mapToWritableMap(map: Map<String, Any?>): WritableMap {
        val writableMap = WritableNativeMap()
        for ((key, value) in map) {
            when (value) {
                null -> writableMap.putNull(key)
                is Boolean -> writableMap.putBoolean(key, value)
                is Double -> writableMap.putDouble(key, value)
                is Int -> writableMap.putInt(key, value)
                is String -> writableMap.putString(key, value)
                is Map<*, *> -> writableMap.putMap(
                    key,
                    mapToWritableMap(value as Map<String, Any?>)
                )

                else -> writableMap.putString(key, value.toString())
            }
        }
        return writableMap
    }

    companion object {
        const val NAME = "HyperModule"
    }
}
