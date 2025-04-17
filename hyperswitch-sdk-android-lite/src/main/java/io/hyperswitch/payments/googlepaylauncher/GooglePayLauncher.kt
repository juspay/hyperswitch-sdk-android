package io.hyperswitch.payments.googlepaylauncher

import android.app.Activity
import android.os.Parcelable
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.payments.view.WidgetLauncher
import kotlinx.parcelize.Parcelize

class GooglePayLauncher() {

    private var activity: Activity? = null

    constructor(
        activity: Activity,
        config: Config,
        readyCallback: GooglePayPaymentMethodLauncher.ReadyCallback,
        resultCallback: GooglePayPaymentMethodLauncher.ResultCallback,
        clientSecret: String? = null
    ) : this() {

        this.activity = activity
        WidgetLauncher.onGPayPaymentResult = resultCallback
        WidgetLauncher.onGPayPaymentReady = readyCallback
        WidgetLauncher.onGPayPaymentReadyWithUI = GooglePayPaymentMethodLauncher.ReadyCallback {
            activity.runOnUiThread {
                WidgetLauncher.onGPayPaymentReady.onReady(it)
            }
        }

        WidgetLauncher.config = config
        val map = mutableMapOf<String, String?>() //Arguments.createMap()
        map.put("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "google_pay")
        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmStatic", String::class.java, MutableMap::class.java)
        confirmCardMethod.invoke(null, "widget", map)
    }

    fun presentForPaymentIntent(clientSecret: String) {
        val map = mutableMapOf<String, String?>()
        val currentActivity = activity ?: return
        map.put("publishableKey", PaymentConfiguration.getInstance(currentActivity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "google_pay")
        map.put("confirm", "true")
        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmStatic", String::class.java, MutableMap::class.java)
        confirmCardMethod.invoke(null, "widget", map)
    }

    @Parcelize
    data class Config(
        val googlePayConfig: GooglePayPaymentMethodLauncher.Config
    ) : Parcelable {
        constructor(
            environment: GooglePayEnvironment,
            merchantCountryCode: String,
            merchantName: String
        ) : this(
            GooglePayPaymentMethodLauncher.Config(
                environment,
                merchantCountryCode,
                merchantName,
                false,
                GooglePayPaymentMethodLauncher.BillingAddressConfig()
            )
        )
    }
}