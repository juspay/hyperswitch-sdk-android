package io.hyperswitch.payments.view

import android.app.Activity
import io.hyperswitch.payments.googlepaylauncher.GooglePayEnvironment
import io.hyperswitch.payments.googlepaylauncher.GooglePayLauncher
import io.hyperswitch.payments.googlepaylauncher.GooglePayPaymentMethodLauncher
import io.hyperswitch.payments.googlepaylauncher.PaymentMethod
import io.hyperswitch.payments.paypallauncher.PayPalPaymentMethodLauncher
import org.json.JSONObject

class WidgetLauncher(
    activity: Activity,
    widgetResId: Int,
    walletType: String,
) {

    private var widgetRes: Int = 1
    private var walletTypeStr: String = ""

    init {
        widgetRes = widgetResId
        walletTypeStr = walletType
    }
    companion object {
        @JvmStatic lateinit var onGPayPaymentReadyWithUI: GooglePayPaymentMethodLauncher.ReadyCallback
        @JvmStatic lateinit var onPaypalPaymentReadyWithUI: PayPalPaymentMethodLauncher.ReadyCallback
        @JvmStatic lateinit var onGPayPaymentReady: GooglePayPaymentMethodLauncher.ReadyCallback
        @JvmStatic lateinit var onPaypalPaymentReady: PayPalPaymentMethodLauncher.ReadyCallback
        @JvmStatic lateinit var onGPayPaymentResult: GooglePayPaymentMethodLauncher.ResultCallback
        @JvmStatic lateinit var onPaypalPaymentResult: PayPalPaymentMethodLauncher.ResultCallback
        @JvmStatic lateinit var config: GooglePayLauncher.Config

        fun onGPayPaymentResultCallBack(paymentResult: String) {
            val jsonObject = JSONObject(paymentResult)
            when (val status = jsonObject.getString("status")) {
                "cancelled" -> onGPayPaymentResult.onResult(GooglePayPaymentMethodLauncher.Result.Canceled(status))
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(jsonObject.getString("message"))
                    throwable.initCause(Throwable(jsonObject.getString("code")))
                    onGPayPaymentResult.onResult(GooglePayPaymentMethodLauncher.Result.Failed(throwable, GooglePayPaymentMethodLauncher.INTERNAL_ERROR))
                }
                else -> onGPayPaymentResult.onResult(
                    GooglePayPaymentMethodLauncher.Result.Completed(PaymentMethod(status, 0, config.googlePayConfig.environment == GooglePayEnvironment.Production)))
            }
        }

        fun onPaypalPaymentResultCallBack(paymentResult: String) {
            val jsonObject = JSONObject(paymentResult)
            when (val status = jsonObject.getString("status")) {
                "cancelled" -> onPaypalPaymentResult.onResult(PayPalPaymentMethodLauncher.Result.Canceled(status))
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(jsonObject.getString("message"))
                    throwable.initCause(Throwable(jsonObject.getString("code")))
                    onPaypalPaymentResult.onResult(PayPalPaymentMethodLauncher.Result.Failed(throwable, PayPalPaymentMethodLauncher.INTERNAL_ERROR))
                }
                else -> onPaypalPaymentResult.onResult(
                    PayPalPaymentMethodLauncher.Result.Completed(io.hyperswitch.payments.paypallauncher.PaymentMethod(status, 0, null)))
            }
        }

    }

}