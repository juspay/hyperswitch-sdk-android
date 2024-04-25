package io.hyperswitch

import android.annotation.SuppressLint
import android.app.Activity
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.react.Utils
import kotlin.reflect.KFunction2

class PaymentSession(val activity: Activity, val paymentIntentClientSecret: String) {

    var completion: KFunction2<() -> Any?,((PaymentResult) -> Unit) -> Unit, Unit>? = null
    private var completion2: ((PaymentResult) -> Unit)? = null
    private var reactInstanceManager: ReactInstanceManager? = null

    init {
        try {
            sharedInstance = this
            val application = activity.application as ReactApplication
            reactInstanceManager = application.reactNativeHost.reactInstanceManager
        } catch (ex: Exception) {
            throw Exception("Please remove \"android:name\" from application tag in AndroidManifest.xml", ex)
        }
    }

    @SuppressLint("VisibleForTests")
    fun initSavedPaymentMethodSession(
        func: KFunction2<() -> Any?, ((PaymentResult) -> Unit) -> Unit, Unit>,
    ) {
        completion = func
        if(reactInstanceManager == null) {
            throw Exception("Payment Session Initialisation Failed")
        } else {
            val reactContext = reactInstanceManager!!.currentReactContext

            if (reactContext == null) {
                reactInstanceManager!!.createReactContextInBackground()
            } else {
                reactInstanceManager!!.recreateReactContextInBackground()
            }
        }
    }

    fun getPaymentSession(getPaymentMethodData: ReadableMap, callback: Callback) {
        activity.runOnUiThread {

            fun getCustomerDefaultSavedPaymentMethodData(): Any? {
                return parseGetPaymentMethodData(getPaymentMethodData)
            }

            fun confirmWithCustomerDefaultPaymentMethod(resultHandler: (PaymentResult) -> Unit) {
                try {
                    completion2 = resultHandler
                    callback.invoke(Arguments.createMap())
                } catch (ex: Exception) {
                    val throwable = Throwable("Not Initialised")
                    throwable.initCause(Throwable("Not Initialised"))
                    resultHandler(PaymentResult.Failed(throwable))
                }
            }

            completion?.let { it(::getCustomerDefaultSavedPaymentMethodData, ::confirmWithCustomerDefaultPaymentMethod) }
        }
    }

    @SuppressLint("VisibleForTests")
    fun exitHeadless(message: ReadableMap) {
        activity.runOnUiThread {
            when (val status = message.getString("status")) {
                "cancelled" -> completion2?.let { it(PaymentResult.Canceled(status)) }
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(message.getString("message"))
                    throwable.initCause(Throwable(message.getString("code")))
                    completion2?.let { it(PaymentResult.Failed(throwable)) }
                }
                else -> completion2?.let { it(PaymentResult.Completed(status ?: "default")) }
            }
//            reactInstanceManager.currentReactContext?.destroy()
//            reactInstanceManager.destroy()
        }
    }

    fun getHyperParams(): WritableMap? {
        val hyperParams = Arguments.createMap();
        hyperParams.putString("appId", activity.packageName)
        hyperParams.putString("country", activity.resources.configuration.locale.country)
        hyperParams.putString("user-agent", Utils.getUserAgent(activity))
        hyperParams.putString("ip", Utils.getDeviceIPAddress(activity))
        hyperParams.putDouble("launchTime", Utils.getCurrentTime())
        return hyperParams
    }

    private fun parseGetPaymentMethodData(readableMap: ReadableMap): Any? {

        val tag = try {
            readableMap.getInt("TAG")
        } catch (ex: Exception) {
            -1
        }
        val dataObject = readableMap.getMap("_0")

        return when (tag) {
            0 -> {
                dataObject?.let {
                    Card(
                        isDefaultPaymentMethod = it.getBoolean("isDefaultPaymentMethod"),
                        paymentToken = it.getString("payment_token") ?: "",
                        cardScheme = it.getString("cardScheme") ?: "",
                        name = it.getString("name") ?: "",
                        expiryDate = it.getString("expiry_date") ?: "",
                        cardNumber = it.getString("cardNumber") ?: "",
                        nickName = it.getString("nick_name") ?: "",
                        cardHolderName = it.getString("cardHolderName") ?: ""
                    )
                }
            }
            1 -> {
                dataObject?.let {
                    Wallet(
                        isDefaultPaymentMethod = it.getBoolean("isDefaultPaymentMethod"),
                        paymentToken = it.getString("payment_token") ?: "",
                        walletType = it.getString("walletType") ?: ""
                    )
                }
            }
            else -> {
                readableMap.let {
                    Error(
                        code = it.getString("code") ?: "",
                        message = it.getString("message") ?: ""
                    )
                }
            }
        }
    }


    companion object {
        @SuppressLint("StaticFieldLeak")
        var sharedInstance: PaymentSession? = null
    }
}

data class Card(
    val isDefaultPaymentMethod: Boolean,
    val paymentToken: String,
    val cardScheme: String,
    val name: String,
    val expiryDate: String,
    val cardNumber: String,
    val nickName: String,
    val cardHolderName: String
)  {
    fun toHashMap(): HashMap<String, Any> {
        val hashMap = HashMap<String, Any>()
        hashMap["isDefaultPaymentMethod"] = isDefaultPaymentMethod
        hashMap["paymentToken"] = paymentToken
        hashMap["cardScheme"] = cardScheme
        hashMap["name"] = name
        hashMap["expiryDate"] = expiryDate
        hashMap["cardNumber"] = cardNumber
        hashMap["nickName"] = nickName
        hashMap["cardHolderName"] = cardHolderName
        return hashMap
    }
}

data class Wallet(
    val isDefaultPaymentMethod: Boolean,
    val paymentToken: String,
    val walletType: String
) {
    fun toHashMap(): HashMap<String, Any> {
        val hashMap = HashMap<String, Any>()
        hashMap["isDefaultPaymentMethod"] = isDefaultPaymentMethod
        hashMap["paymentToken"] = paymentToken
        hashMap["walletType"] = walletType
        return hashMap
    }
}

data class Error(
    val code: String,
    val message: String,
) {
    fun toHashMap(): HashMap<String, Any> {
        val hashMap = HashMap<String, Any>()
        hashMap["code"] = code
        hashMap["message"] = message
        return hashMap
    }
}