package io.hyperswitch.view

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import io.hyperswitch.BuildConfig
import io.hyperswitch.paymentsession.Callback
import io.hyperswitch.paymentsession.EventCallback
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.WidgetCallbackManager
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.ReactNativeController
import java.util.UUID

enum class WidgetType {
    PAYMENT_SHEET, CARD, EXPRESS_CHECKOUT, GOOGLE_PAY, PAYPAL
}

class PaymentWidget : FrameLayout {

    private var configuration: PaymentSheet.Configuration? = null
    private var callback: Callback? = null
    private var publishableKey: String? = null
    private var profileId: String? = null
    private val sessionId = UUID.randomUUID().toString()

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

    fun setWidgetType(widgetType: WidgetType) {
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
        val mCallback: Callback = { result ->
            callback(result)
            removeWidget()
        }
        WidgetCallbackManager.setCallback(mCallback, true, this.sessionId)
    }

    fun onEvent(eventCallback: EventCallback) {
        WidgetCallbackManager.setEventCallback(this.sessionId, eventCallback)
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

    private fun getWidgetType(): String {
        return when (this.widgetType) {
            WidgetType.PAYMENT_SHEET -> "widgetPaymentSheet"
            WidgetType.CARD -> "card"
            WidgetType.EXPRESS_CHECKOUT -> "expressCheckout"
            WidgetType.GOOGLE_PAY -> "google_pay"
            WidgetType.PAYPAL -> "paypal"
//            WidgetType.SAVED_PAYMENTS -> "widgetSavedPayments"
            else -> "widgetPaymentSheet"
        }
    }

    fun showWidget(clientSecret: String, callback: Callback? = null) {
        val activity = getFragmentActivity()
            ?: throw IllegalStateException("PaymentWidget must be attached to a FragmentActivity")
        if (id == NO_ID) {
            id = generateViewId()
        }
        callback?.let {
            this.onPaymentResult(callback)
        }
        if (clientSecret == "null") {
            throw IllegalArgumentException("Client Secret cannot be null")
        }
        val fragment = HyperFragment.Builder()
            .setComponentName("hyperSwitch")
            .setLaunchOptions(
                launchOptions.getBundle(
                    paymentIntentClientSecret = clientSecret,
                    configuration = this.configuration,
                    type=getWidgetType(),
                    sessionId = this.sessionId
                )
            )
            .build()
        activity.supportFragmentManager
            .beginTransaction()
            .replace(this.id, fragment)
            .commitAllowingStateLoss()
    }

    fun showWidget(clientSecret: String) {
        showWidget(clientSecret, this.callback)
    }

    private fun removeWidget() {
        val activity = getFragmentActivity() ?: return
        val fragment = activity.supportFragmentManager.findFragmentById(id)
                as? HyperFragment ?: return
        this.callback = null
        WidgetCallbackManager.removeSession(this.sessionId)
        activity.supportFragmentManager.beginTransaction()
            .remove(fragment)
            .commitAllowingStateLoss()
    }
}