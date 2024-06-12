package io.hyperswitch.react

import android.annotation.SuppressLint
import com.facebook.react.bridge.*
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentSession
import io.hyperswitch.PaymentSessionHandler
import io.hyperswitch.payments.paymentlauncher.PaymentResult

class HyperHeadlessModule internal constructor(rct: ReactApplicationContext) : ReactContextBaseJavaModule(rct) {

    override fun getName(): String {
        return "HyperHeadless"
    }

    // Method to initialise the payment session
    @ReactMethod
    fun initialisePaymentSession(callback: Callback) {
        // Check if a payment session is already initialised
        if (PaymentSession.completion != null) {
            // Create a map to store payment session details
            val map = Arguments.createMap()
            // Add publishable key to the map
            map.putString("publishableKey", PaymentConfiguration.pkKey)
            // Add client secret to the map
            map.putString("clientSecret", PaymentSession.paymentIntentClientSecret)
            // Add hyper parameters to the map
            map.putMap("hyperParams", PaymentSession.getHyperParams())
            // Invoke the callback with the map
            callback.invoke(map)
        }
    }

    // Method to get the payment session
    @ReactMethod
    fun getPaymentSession(getPaymentMethodData: ReadableMap, getPaymentMethodData2: ReadableMap, getPaymentMethodDataArray: ReadableArray, callback: Callback) {
        // Call the getPaymentSession method from PaymentSession singleton
        PaymentSession.getPaymentSession(getPaymentMethodData, getPaymentMethodData2, getPaymentMethodDataArray, callback)
    }

    // Method to exit the headless mode
    @ReactMethod
    fun exitHeadless(status: ReadableMap) {
        // Call the exitHeadless method from PaymentSession singleton
        PaymentSession.exitHeadless(status)
    }
}
