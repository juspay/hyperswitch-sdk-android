package io.hyperswitch.view

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import io.hyperswitch.BuildConfig
import io.hyperswitch.payments.Callback
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.ReactNativeController

enum class WidgetType {
    PAYMENT_SHEET, CARD, EXPRESS_CHECKOUT, GOOGLE_PAY, PAYPAL
}

class PaymentWidget : FrameLayout {

    private var configuration: PaymentSheet.Configuration? = null
    private var callback: Callback? = null
    private var publishableKey: String? = null
    private var profileId: String? = null

    private var launchOptions = LaunchOptions(context, BuildConfig.VERSION_NAME)

    private var widgetType = WidgetType.PAYMENT_SHEET


    constructor(context: Context?) : super(context!!) {
    }

    fun initWidget(publishableKey: String) {
        initWidget(publishableKey, this.profileId ?: "")
    }

    fun initWidget(
        publishableKey: String, profileId: String
    ) {
        initWidget(
            context.applicationContext as Application,
            WidgetType.PAYMENT_SHEET,
            publishableKey,
            profileId
        )
    }

    fun setWidgetType(widgetType: WidgetType){
        this.widgetType = widgetType
    }
    fun initWidget(
        application: Application,
        type: WidgetType,
        publishableKey: String,
        profileId: String,
    ) {
        this.widgetType = type
        this.publishableKey = publishableKey
        this.profileId = profileId
        ReactNativeController.initialize(application)
    }

    fun configuration(configuration: PaymentSheet.Configuration) {
        this.configuration = configuration
    }

    fun onPaymentResult(callback: Callback) {
        this.callback = callback
    }

    private fun getFragmentActivity(): FragmentActivity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is FragmentActivity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    private fun getWidgetType(widgetType: WidgetType?): String {
        return when (widgetType) {
            WidgetType.PAYMENT_SHEET -> "widgetPaymentSheet"
            WidgetType.CARD -> "card"
            WidgetType.EXPRESS_CHECKOUT -> "expressCheckout"
            WidgetType.GOOGLE_PAY -> "google_pay"
            WidgetType.PAYPAL -> "paypal"
//            WidgetType.SAVED_PAYMENTS -> "widgetSavedPayments"
            else -> "widgetPaymentSheet"
        }
    }

    fun showWidget(clientSecret: String, callback: Callback){

    }


    fun showWidget(clientSecret: String) {
        val activity = getFragmentActivity()
            ?: throw IllegalStateException("PaymentWidget must be attached to a FragmentActivity")

        if (id == NO_ID) {
            id = generateViewId()
        }
        if(clientSecret == "null"){
            throw Exception("Client Secret cannot be null")
        }

        clientSecret.let {
            val fragment = HyperFragment.Builder(
            ).setComponentName("hyperSwitch")
                .setLaunchOptions(
                    launchOptions.getBundle(
                        clientSecret,
                        configuration,
                        getWidgetType(widgetType)
                    )
                )
                .build()

            activity.supportFragmentManager.beginTransaction().replace(this.id, fragment)
                .commitAllowingStateLoss()
        }
    }
}