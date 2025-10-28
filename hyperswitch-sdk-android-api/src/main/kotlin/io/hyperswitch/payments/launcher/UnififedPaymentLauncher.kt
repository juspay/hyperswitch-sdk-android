package io.hyperswitch.payments.launcher

import android.app.Activity
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.model.ConfirmPaymentIntentParams
import io.hyperswitch.payments.expresscheckoutlauncher.ExpressCheckoutPaymentMethodLauncher
import io.hyperswitch.payments.googlepaylauncher.Config as GooglePayConfig
import io.hyperswitch.payments.googlepaylauncher.GooglePayPaymentMethodLauncher
import io.hyperswitch.payments.paymentlauncher.PaymentResultCallback
import io.hyperswitch.payments.paypallauncher.PayPalPaymentMethodLauncher
import io.hyperswitch.payments.view.WidgetLauncher

enum class PaymentMethod(val apiValue: String) {
    CARD("card"),
    GOOGLE_PAY("google_pay"),
    PAYPAL("paypal"),
    EXPRESS_CHECKOUT("expressCheckout");
}

class UnifiedPaymentLauncher private constructor(
    private val activity: Activity,
    private val paymentMethod: PaymentMethod
) {
    private var googlePayConfigInstance: GooglePayConfig? = null

    /**
     * Confirms a card payment. This method is specific to CARD payment methods.
     * It replicates the logic from the original PaymentLauncher.
     */
    fun confirmCardPayment(params: ConfirmPaymentIntentParams) {
        if (paymentMethod != PaymentMethod.CARD) {
            throw IllegalStateException("confirmCardPayment method is only for Card payments.")
        }
        val map = mutableMapOf<String, String?>()
        val paymentConfiguration = PaymentConfiguration.getInstance(activity)
        map["publishableKey"] = paymentConfiguration.publishableKey
        map["stripeAccountId"] = paymentConfiguration.stripeAccountId
        map["clientSecret"] = params[0].toString()
        map["paymentMethodData"] = params[1].toString()
        map["paymentMethodType"] = PaymentMethod.CARD.apiValue

        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val confirmCardMethod = hyperModuleClass.getMethod("confirmCardStatic", Map::class.java)
        confirmCardMethod.invoke(null, map)
    }

    /**
     * Presents the payment interface or confirms the payment for Google Pay, PayPal, and Express Checkout.
     * For Google Pay and PayPal, this initiates the payment flow.
     * For Express Checkout, this confirms the payment (analogous to EC's original confirm method).
     */
    fun presentForPayment(clientSecret: String) {
        if (paymentMethod == PaymentMethod.CARD) {
            throw IllegalStateException("presentForPayment is not for Card payments. Use confirmCardPayment().")
        }

        val map = mutableMapOf<String, String?>()
        map["publishableKey"] = PaymentConfiguration.getInstance(activity).publishableKey
        map["clientSecret"] = clientSecret
        map["paymentMethodType"] = paymentMethod.apiValue
        map["confirm"] = "true"

        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        var methodName = "confirmStatic"
        var methodParams: Array<Class<*>> = arrayOf(String::class.java, MutableMap::class.java)
        var methodArgs: Array<Any?> = arrayOf("widget", map)

        if (paymentMethod == PaymentMethod.EXPRESS_CHECKOUT) {
            methodName = "confirmECStatic"
            methodParams = arrayOf(MutableMap::class.java)
            methodArgs = arrayOf(map)
            map["paymentMethodData"] = PaymentMethod.EXPRESS_CHECKOUT.apiValue
        }

        val method = hyperModuleClass.getMethod(methodName, *methodParams)
        method.invoke(null, *methodArgs)
    }

    /**
     * Initializes the payment method for widget-based flows (GPay, PayPal, EC)
     * by making the initial call to HyperModule.
     */
    private fun initializePaymentMethod(clientSecret: String?) {
        val map = mutableMapOf<String, String?>()
        map["publishableKey"] = PaymentConfiguration.getInstance(activity).publishableKey
        map["clientSecret"] = clientSecret
        map["paymentMethodType"] = paymentMethod.apiValue

        val hyperModuleClass = Class.forName("io.hyperswitch.react.HyperModule")
        val methodName: String
        val methodParams: Array<Class<*>>
        val methodArgs: Array<Any?>

        when (paymentMethod) {
            PaymentMethod.GOOGLE_PAY, PaymentMethod.PAYPAL -> {
                methodName = "confirmStatic"
                methodParams = arrayOf(String::class.java, MutableMap::class.java)
                methodArgs = arrayOf("widget", map)
            }
            PaymentMethod.EXPRESS_CHECKOUT -> {
                methodName = "confirmECStatic"
                methodParams = arrayOf(MutableMap::class.java)
                methodArgs = arrayOf(map)
            }
            PaymentMethod.CARD -> {
                // Card payments don't have an explicit init call like this via HyperModule in PaymentLauncher
                return
            }
        }
        val initMethod = hyperModuleClass.getMethod(methodName, *methodParams)
        initMethod.invoke(null, *methodArgs)
    }


    companion object {
        fun createCardLauncher(
            activity: Activity,
            resultCallback: PaymentResultCallback
        ): UnifiedPaymentLauncher {
            WidgetLauncher.onCurrentCardResult = resultCallback
            return UnifiedPaymentLauncher(activity, PaymentMethod.CARD)
        }

        fun createGooglePayLauncher(
            activity: Activity,
            clientSecret: String?,
            config: GooglePayConfig,
            readyCallback: GooglePayPaymentMethodLauncher.ReadyCallback,
            resultCallback: GooglePayPaymentMethodLauncher.ResultCallback
        ): UnifiedPaymentLauncher {
            WidgetLauncher.onCurrentPaymentReady = { isReady ->
                activity.runOnUiThread {
                    readyCallback.onReady(isReady)
                }
            }
            WidgetLauncher.onCurrentGPayResult = resultCallback
            WidgetLauncher.config = config

            val launcher = UnifiedPaymentLauncher(activity, PaymentMethod.GOOGLE_PAY)
            launcher.googlePayConfigInstance = config
            launcher.initializePaymentMethod(clientSecret)
            return launcher
        }

        fun createPayPalLauncher(
            activity: Activity,
            clientSecret: String?,
            readyCallback: PayPalPaymentMethodLauncher.ReadyCallback,
            resultCallback: PayPalPaymentMethodLauncher.ResultCallback
        ): UnifiedPaymentLauncher {
            WidgetLauncher.onCurrentPaymentReady = { isReady ->
                activity.runOnUiThread {
                    readyCallback.onReady(isReady)
                }
            }
            WidgetLauncher.onCurrentPayPalResult = resultCallback

            val launcher = UnifiedPaymentLauncher(activity, PaymentMethod.PAYPAL)
            launcher.initializePaymentMethod(clientSecret)
            return launcher
        }

        fun createExpressCheckoutLauncher(
            activity: Activity,
            clientSecret: String?,
            readyCallback: ExpressCheckoutPaymentMethodLauncher.ReadyCallback,
            resultCallback: ExpressCheckoutPaymentMethodLauncher.ResultCallback
        ): UnifiedPaymentLauncher {
            WidgetLauncher.onCurrentPaymentReady = { isReady ->
                activity.runOnUiThread {
                    readyCallback.onReady(isReady) // Original readyCallback
                }
            }
            WidgetLauncher.onCurrentExpressCheckoutResult = resultCallback

            val launcher = UnifiedPaymentLauncher(activity, PaymentMethod.EXPRESS_CHECKOUT)
            launcher.initializePaymentMethod(clientSecret)
            return launcher
        }
    }
} 