package io.hyperswitch.payments.paypallauncher

import android.app.Activity
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.payments.view.WidgetLauncher

class PayPalLauncher() {
    private var activity:Activity? = null
    constructor(
        activity: Activity,
        readyCallback: PayPalPaymentMethodLauncher.ReadyCallback,
        resultCallback: PayPalPaymentMethodLauncher.ResultCallback,
        clientSecret: String? = null
    ) : this() {
        this.activity=activity
        WidgetLauncher.onPaypalPaymentResult = resultCallback
        WidgetLauncher.onPaypalPaymentReady = readyCallback
        WidgetLauncher.onPaypalPaymentReadyWithUI = PayPalPaymentMethodLauncher.ReadyCallback {
            activity.runOnUiThread {
                WidgetLauncher.onPaypalPaymentReady.onReady(it)
            }
        }
        val map = mutableMapOf<String,String?>()
        map.put("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "paypal")
        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmStatic", String::class.java, MutableMap::class.java)
        confirmCardMethod.invoke(null, "widget", map)
    }

    fun presentForPaymentIntent(clientSecret: String) {
        val map = mutableMapOf<String,String?>()
        val currentActivity=activity?:return
        map.put("publishableKey", PaymentConfiguration.getInstance(currentActivity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "paypal")
        map.put("confirm", "true")
        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmStatic", String::class.java, MutableMap::class.java)
        confirmCardMethod.invoke(null, "widget", map)
    }
}