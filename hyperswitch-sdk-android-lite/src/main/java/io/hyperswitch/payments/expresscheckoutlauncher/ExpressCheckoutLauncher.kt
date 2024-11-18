package io.hyperswitch.payments.expresscheckoutlauncher

import android.app.Activity
import io.hyperswitch.paymentsheet.PaymentSheet

class ExpressCheckoutLauncher() {
    constructor(
        activity: Activity,
        clientSecret: String? = null,
        configuration: PaymentSheet.Configuration?,
        readyCallback: ExpressCheckoutPaymentMethodLauncher.ReadyCallback,
        resultCallback: ExpressCheckoutPaymentMethodLauncher.ResultCallback,
    ) : this() {

//        val map = Arguments.createMap()
//        map.putString("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
//        map.putString("clientSecret", clientSecret)
//        map.putString("paymentMethodType", "expressCheckout")
//        HyperModule.confirmEC(map)
    }

    fun confirm() {
//        val map = Arguments.createMap()
//        map.putString("paymentMethodType", "expressCheckout")
//        map.putString("paymentMethodData", "expressCheckout")
//        HyperModule.confirmEC(map)
    }

}