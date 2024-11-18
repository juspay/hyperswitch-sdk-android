package io.hyperswitch.paymentsheet

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration

//import io.hyperswitch.react.Utils

class DefaultFlowController(
    var activity: Activity,
    override var shippingDetails: AddressDetails?,
    var paymentIntentClientSecret: String,
    var configuration: PaymentSheet.Configuration?
) : PaymentSheet.FlowController {

    override fun configureWithPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?,
        callback: PaymentSheet.FlowController.ConfigCallback
    ) {
        this.paymentIntentClientSecret = paymentIntentClientSecret
        this.configuration = configuration
        callback.onConfigured(true, null)
    }

    override fun configureWithSetupIntent(
        setupIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?,
        callback: PaymentSheet.FlowController.ConfigCallback
    ) {
        paymentIntentClientSecret = setupIntentClientSecret
        this.configuration = configuration
        callback.onConfigured(true, null)
    }

    override fun getPaymentOption(): PaymentOption? {
        return null
    }

    override fun presentPaymentOptions() {
        val bundle = Bundle()
        bundle.putString(
            "publishableKey", PaymentConfiguration.getInstance(activity).publishableKey
        )
        bundle.putString("clientSecret", paymentIntentClientSecret)
        bundle.putParcelable("configuration", configuration)
//        Utils.openReactView(activity, bundle, "addCard", null)
    }

    override fun confirm() {
        val bundle = Bundle()
        // bundle.putString("publishableKey", PaymentConfiguration.getInstance(activity).publishableKey)
        bundle.putString("clientSecret", paymentIntentClientSecret)
        bundle.putParcelable("configuration", configuration)
//        Utils.openReactView(activity, bundle, "confirmCard", null)
    }
}
