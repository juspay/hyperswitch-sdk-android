package io.hyperswitch.payments.expresscheckoutlauncher

import android.app.Activity
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsheet.PaymentSheet

class ExpressCheckoutLauncher() {
    private var activity: Activity? = null

    constructor(
        activity: Activity,
        clientSecret: String? = null,
        configuration: PaymentSheet.Configuration?,
        readyCallback: ExpressCheckoutPaymentMethodLauncher.ReadyCallback,
        resultCallback: ExpressCheckoutPaymentMethodLauncher.ResultCallback,
    ) : this() {
        this.activity = activity
        WidgetLauncher.onExpressCheckoutPaymentResult = resultCallback
        WidgetLauncher.onExpressCheckoutPaymentReady = readyCallback
        WidgetLauncher.onExpressCheckoutPaymentReadyWithUI =
            ExpressCheckoutPaymentMethodLauncher.ReadyCallback {
                activity.runOnUiThread {
                    WidgetLauncher.onExpressCheckoutPaymentReady.onReady(it)
                }
            }
        val map = mutableMapOf<String, String?>()
        map.put("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "expressCheckout")


        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmECStatic", MutableMap::class.java)
        confirmCardMethod.invoke(null, map)
    }

    fun confirm(clientSecret: String) {

        val map = mutableMapOf<String, String?>()
        val currentActivity = activity ?: return
        map.put("publishableKey", PaymentConfiguration.getInstance(currentActivity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "expressCheckout")
        map.put("paymentMethodData", "expressCheckout")
        map.put("confirm", "true")
        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmECStatic", MutableMap::class.java)
        confirmCardMethod.invoke(null, map)
    }
}
