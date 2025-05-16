package io.hyperswitch.payments.view

import android.app.Activity
import io.hyperswitch.payments.launcher.PaymentMethod as UnifiedPaymentMethodEnum
import io.hyperswitch.payments.expresscheckoutlauncher.ExpressCheckoutPaymentMethodLauncher
import io.hyperswitch.payments.googlepaylauncher.GooglePayEnvironment
import io.hyperswitch.payments.googlepaylauncher.Config as GooglePayConfig
import io.hyperswitch.payments.googlepaylauncher.GooglePayPaymentMethodLauncher
import io.hyperswitch.payments.launcher.PaymentMethod
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.payments.paymentlauncher.PaymentResultCallback
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
        @JvmStatic var onCurrentPaymentReady: ((Boolean) -> Unit)? = null

        @JvmStatic var onCurrentGPayResult: GooglePayPaymentMethodLauncher.ResultCallback? = null
        @JvmStatic var onCurrentPayPalResult: PayPalPaymentMethodLauncher.ResultCallback? = null
        @JvmStatic var onCurrentExpressCheckoutResult: ExpressCheckoutPaymentMethodLauncher.ResultCallback? = null
        @JvmStatic var onCurrentCardResult: PaymentResultCallback? = null

        @JvmStatic lateinit var config: GooglePayConfig

        /**
         * Called from native code (e.g., React Native) when the payment widget is ready.
         */
        @JvmStatic
        fun onPaymentReadyCallback(isReady: Boolean) {
            onCurrentPaymentReady?.invoke(isReady)
        }

        /**
         * Called from native code (e.g., React Native) with the payment result.
         */
        @JvmStatic
        fun onPaymentResultCallback(paymentMethodTypeStr: String, paymentResult: String) {
            val jsonObject = JSONObject(paymentResult)
            val status = jsonObject.optString("status") // Use optString for safety

            val paymentMethodType = PaymentMethod.entries.find { it.apiValue == paymentMethodTypeStr }

            when (paymentMethodType) {
                UnifiedPaymentMethodEnum.GOOGLE_PAY -> {
                    val callback = onCurrentGPayResult ?: return
                    when (status) {
                        "cancelled" -> callback.onResult(GooglePayPaymentMethodLauncher.Result.Canceled(status))
                        "failed", "requires_payment_method" -> {
                            val throwable = Throwable(jsonObject.optString("message"))
                            jsonObject.optString("code").let { code -> if (code.isNotEmpty()) throwable.initCause(Throwable(code)) }
                            callback.onResult(GooglePayPaymentMethodLauncher.Result.Failed(throwable, GooglePayPaymentMethodLauncher.INTERNAL_ERROR))
                        }
                        else -> callback.onResult(
                            GooglePayPaymentMethodLauncher.Result.Completed(
                                io.hyperswitch.payments.googlepaylauncher.PaymentMethod(status, 0, config.googlePayConfig.environment == GooglePayEnvironment.Production)
                            )
                        )
                    }
                }
                UnifiedPaymentMethodEnum.PAYPAL -> {
                    val callback = onCurrentPayPalResult ?: return
                    when (status) {
                        "cancelled" -> callback.onResult(PayPalPaymentMethodLauncher.Result.Canceled(status))
                        "failed", "requires_payment_method" -> {
                            val throwable = Throwable(jsonObject.optString("message"))
                            jsonObject.optString("code").let { code -> if (code.isNotEmpty()) throwable.initCause(Throwable(code)) }
                            callback.onResult(PayPalPaymentMethodLauncher.Result.Failed(throwable, PayPalPaymentMethodLauncher.INTERNAL_ERROR))
                        }
                        else -> callback.onResult(
                            PayPalPaymentMethodLauncher.Result.Completed(
                                io.hyperswitch.payments.paypallauncher.PaymentMethod(status, 0, null)
                            )
                        )
                    }
                }
                UnifiedPaymentMethodEnum.EXPRESS_CHECKOUT -> {
                    val callback = onCurrentExpressCheckoutResult ?: return
                    when (status) {
                        "cancelled" -> callback.onResult(ExpressCheckoutPaymentMethodLauncher.Result.Canceled(status))
                        "failed", "requires_payment_method" -> {
                            val throwable = Throwable(jsonObject.optString("message"))
                            jsonObject.optString("code").let { code -> if (code.isNotEmpty()) throwable.initCause(Throwable(code)) }
                            callback.onResult(ExpressCheckoutPaymentMethodLauncher.Result.Failed(throwable, ExpressCheckoutPaymentMethodLauncher.INTERNAL_ERROR))
                        }
                        else -> callback.onResult(
                            ExpressCheckoutPaymentMethodLauncher.Result.Completed(
                                io.hyperswitch.payments.expresscheckoutlauncher.PaymentMethod(status, 0, null)
                            )
                        )
                    }
                }
                UnifiedPaymentMethodEnum.CARD -> {
                    val callback = onCurrentCardResult ?: return
                    when (status) {
                        "cancelled" -> callback.onPaymentResult(PaymentResult.Canceled(status))
                        "failed", "requires_payment_method" -> {
                            val message = jsonObject.optString("message")
                            val code = jsonObject.optString("code")
                            val throwable = Throwable(message)
                            if (code.isNotEmpty()) throwable.initCause(Throwable(code))
                            callback.onPaymentResult(PaymentResult.Failed(throwable))
                        }
                        else -> callback.onPaymentResult(PaymentResult.Completed(status))
                    }
                }
                null -> {
                    System.err.println("WidgetLauncher: Received payment result with unknown type: $paymentMethodTypeStr")
                }
            }
        }
    }
}