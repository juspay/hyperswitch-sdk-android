package io.hyperswitch.payments.paypallauncher

import androidx.appcompat.app.AppCompatActivity

class PayPalLauncher() {
    constructor(
        activity: AppCompatActivity,
        readyCallback: PayPalPaymentMethodLauncher.ReadyCallback,
        resultCallback: PayPalPaymentMethodLauncher.ResultCallback,
        clientSecret: String? = null
    ) : this() {
//        val map = Arguments.createMap()
//        map.putString("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
//        map.putString("clientSecret", clientSecret)
//        map.putString("paymentMethodType", "paypal")
//        HyperModule.confirm("widget", map)
    }

    fun presentForPaymentIntent(clientSecret: String) {
//        val map = Arguments.createMap()
//        // map.putString("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
//        map.putString("clientSecret", clientSecret)
//        map.putString("paymentMethodType", "paypal")
//        map.putBoolean("confirm", true)
//        HyperModule.confirm("widget", map)
    }
}