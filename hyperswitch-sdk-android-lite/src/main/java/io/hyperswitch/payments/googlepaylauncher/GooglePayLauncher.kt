package io.hyperswitch.payments.googlepaylauncher

import android.app.Activity
import android.os.Parcelable
import io.hyperswitch.PaymentConfiguration
import kotlinx.parcelize.Parcelize

class GooglePayLauncher() {

    constructor(
        activity: Activity,
        config: Config,
        readyCallback: GooglePayPaymentMethodLauncher.ReadyCallback,
        resultCallback: GooglePayPaymentMethodLauncher.ResultCallback,
        clientSecret: String? = null
    ) : this() {
        val map = mutableMapOf<String, String?>() //Arguments.createMap()
        map.put("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "google_pay")
//        HyperModule.confirm("widget",map)
    }

    fun presentForPaymentIntent(clientSecret: String) {
        val map = mutableMapOf<String, String?>() //Arguments.createMap()
        // map.putString("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        map.put("clientSecret", clientSecret)
        map.put("paymentMethodType", "google_pay")
        map.put("confirm", "true")
//        HyperModule.confirm("widget",map)
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