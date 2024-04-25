package io.hyperswitch.react

import com.facebook.react.bridge.*
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentSession

class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) : ReactContextBaseJavaModule(rct) {

    override fun getName(): String {
        return "HyperHeadless"
    }

    // Method to initialise the payment session
    @ReactMethod
    fun initialisePaymentSession(callback: Callback) {
        // Check if a payment session is already initialised
        if (PaymentSession.sharedInstance?.completion != null) {
            // Create a map to store payment session details
            val map = Arguments.createMap()
            // Add publishable key to the map
            map.putString("publishableKey", PaymentConfiguration.pkKey)
            // Add client secret to the map
            map.putString("clientSecret", PaymentSession.sharedInstance?.paymentIntentClientSecret)
            // Add hyper parameters to the map
            map.putMap("hyperParams", PaymentSession.sharedInstance?.getHyperParams())
            // Invoke the callback with the map
            callback.invoke(map)
        }
    }

    // Method to get the payment session
    @ReactMethod
    fun getPaymentSession(getPaymentMethodData: ReadableMap, getPaymentMethodDataArray: ReadableArray, callback: Callback) {
        // Call the getPaymentSession method from PaymentSession singleton
        PaymentSession.sharedInstance?.getPaymentSession(getPaymentMethodData, callback)
    }

    // Method to exit the headless mode
    @ReactMethod
    fun exitHeadless(status: ReadableMap) {
        // Call the exitHeadless method from PaymentSession singleton
        PaymentSession.sharedInstance?.exitHeadless(status)
    }
}
