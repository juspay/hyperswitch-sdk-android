package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.isPresented
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.paymentIntentClientSecret
import io.hyperswitch.paymentsession.ExitHeadlessCallBackManager
import io.hyperswitch.paymentsession.GetPaymentSessionCallBackManager
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsession.PaymentSessionHandler

class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    override fun getName(): String {
        return "HyperHeadless"
    }

    // Method to initialise the payment session
    @ReactMethod
    fun initialisePaymentSession(callback: Callback) {
        if (GetPaymentSessionCallBackManager.getCallback() != null || !isPresented) {
            callback.invoke(
                Arguments.fromBundle(
                    LaunchOptions().getBundle(
                        rct,
                        paymentIntentClientSecret ?: ""
                    ).getBundle("props")
                )
            )
        }
    }

    // Method to get the payment session
    @ReactMethod
    fun getPaymentSession(
        getPaymentMethodData: ReadableMap,
        getPaymentMethodData2: ReadableMap,
        getPaymentMethodDataArray: ReadableArray,
        callback: Callback
    ) {
        val handler = object : PaymentSessionHandler {
            override fun getCustomerDefaultSavedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData)
            }

            override fun getCustomerLastUsedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData2)
            }

            override fun getCustomerSavedPaymentMethodData(): Array<PaymentMethod> {
                val array = mutableListOf<PaymentMethod>()
                for (i in 0 until getPaymentMethodDataArray.size()) {
                    array.add(parseGetPaymentMethodData(getPaymentMethodDataArray.getMap(i)))
                }
                return array.toTypedArray()
            }

            override fun confirmWithCustomerDefaultPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData.getMap("_0")?.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerLastUsedPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData2.getMap("_0")?.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerPaymentToken(
                paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                try {
                    ExitHeadlessCallBackManager.setCallback(resultHandler)
                    val map = Arguments.createMap()
                    map.putString("paymentToken", paymentToken)
                    map.putString("cvc", cvc)
                    callback.invoke(map)
                } catch (ex: Exception) {
                    val throwable = Throwable("Not Initialised")
                    throwable.initCause(Throwable("Not Initialised"))
                    resultHandler(PaymentResult.Failed(throwable))
                }
            }
        }
        GetPaymentSessionCallBackManager.executeCallback(handler)
    }

    private fun parseGetPaymentMethodData(readableMap: ReadableMap): PaymentMethod {

        val tag = try {
            readableMap.getString("TAG")
        } catch (ex: Exception) {
            ""
        }
        val dataObject: ReadableMap = readableMap.getMap("_0") ?: Arguments.createMap()

        return when (tag) {
            "SAVEDLISTCARD" -> {
                PaymentMethod.Card(
                    isDefaultPaymentMethod = dataObject.getBoolean("isDefaultPaymentMethod"),
                    paymentToken = dataObject.getString("payment_token") ?: "",
                    cardScheme = dataObject.getString("cardScheme") ?: "",
                    name = dataObject.getString("name") ?: "",
                    expiryDate = dataObject.getString("expiry_date") ?: "",
                    cardNumber = dataObject.getString("cardNumber") ?: "",
                    nickName = dataObject.getString("nick_name") ?: "",
                    cardHolderName = dataObject.getString("cardHolderName") ?: "",
                    requiresCVV = dataObject.getBoolean("requiresCVV"),
                    created = dataObject.getString("created") ?: "",
                    lastUsedAt = dataObject.getString("lastUsedAt") ?: "",
                )
            }

            "SAVEDLISTWALLET" -> {
                PaymentMethod.Wallet(
                    isDefaultPaymentMethod = dataObject.getBoolean("isDefaultPaymentMethod"),
                    paymentToken = dataObject.getString("payment_token") ?: "",
                    walletType = dataObject.getString("walletType") ?: "",
                    created = dataObject.getString("created") ?: "",
                    lastUsedAt = dataObject.getString("lastUsedAt") ?: "",
                )
            }

            else -> {
                PaymentMethod.Error(
                    code = readableMap.getString("code") ?: "",
                    message = readableMap.getString("message") ?: ""
                )
            }
        }
    }


    // Method to exit the headless mode
    @ReactMethod
    fun exitHeadless(status: String) {
        ExitHeadlessCallBackManager.executeCallback(status)
        // reactInstanceManager?.currentReactContext?.destroy()
        // reactInstanceManager?.destroy()
    }
}
