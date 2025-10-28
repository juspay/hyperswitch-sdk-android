package io.hyperswitch.payments.view

import android.app.Activity
import io.hyperswitch.payments.launcher.PaymentMethod as UnifiedPaymentMethodEnum
import io.hyperswitch.payments.expresscheckoutlauncher.ExpressCheckoutPaymentMethodLauncher
import io.hyperswitch.payments.GooglePayEnvironment
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
        @JvmStatic
        fun onPaymentReadyCallback(isReady: Boolean) {
            onCurrentPaymentReady?.invoke(isReady)
        }

        @JvmStatic
        fun onPaymentResultCallback(paymentMethodTypeStr: String, paymentResult: String) {
            val jsonObject = JSONObject(paymentResult)
            val status = jsonObject.optString("status")
            val message = jsonObject.optString("message")
            val code = jsonObject.optString("code")

            val paymentMethodType = PaymentMethod.entries.find { it.apiValue == paymentMethodTypeStr }

            when (paymentMethodType) {
                UnifiedPaymentMethodEnum.GOOGLE_PAY -> {
                    handlePaymentResult(
                        status = status,
                        message = message,
                        code = code,
                        onCancelled = { reason ->
                            onCurrentGPayResult?.onResult(GooglePayPaymentMethodLauncher.Result.Canceled(reason))
                        },
                        onFailed = { error, errorCode ->
                            onCurrentGPayResult?.onResult(GooglePayPaymentMethodLauncher.Result.Failed(error, errorCode))
                        },
                        onCompleted = {
                            onCurrentGPayResult?.onResult(
                                GooglePayPaymentMethodLauncher.Result.Completed(
                                    io.hyperswitch.payments.googlepaylauncher.PaymentMethod(
                                        status,
                                        0,
                                        config.googlePayConfig.environment == GooglePayEnvironment.Production
                                    )
                                )
                            )
                        },
                        defaultErrorCode = GooglePayPaymentMethodLauncher.INTERNAL_ERROR
                    )
                }
                UnifiedPaymentMethodEnum.PAYPAL -> {
                    handlePaymentResult(
                        status = status,
                        message = message,
                        code = code,
                        onCancelled = { reason ->
                            onCurrentPayPalResult?.onResult(PayPalPaymentMethodLauncher.Result.Canceled(reason))
                        },
                        onFailed = { error, errorCode ->
                            onCurrentPayPalResult?.onResult(PayPalPaymentMethodLauncher.Result.Failed(error, errorCode))
                        },
                        onCompleted = {
                            onCurrentPayPalResult?.onResult(
                                PayPalPaymentMethodLauncher.Result.Completed(
                                    io.hyperswitch.payments.paypallauncher.PaymentMethod(status, 0, null)
                                )
                            )
                        },
                        defaultErrorCode = PayPalPaymentMethodLauncher.INTERNAL_ERROR
                    )
                }
                UnifiedPaymentMethodEnum.EXPRESS_CHECKOUT -> {
                    handlePaymentResult(
                        status = status,
                        message = message,
                        code = code,
                        onCancelled = { reason ->
                            onCurrentExpressCheckoutResult?.onResult(ExpressCheckoutPaymentMethodLauncher.Result.Canceled(reason))
                        },
                        onFailed = { error, errorCode ->
                            onCurrentExpressCheckoutResult?.onResult(ExpressCheckoutPaymentMethodLauncher.Result.Failed(error, errorCode))
                        },
                        onCompleted = {
                            onCurrentExpressCheckoutResult?.onResult(
                                ExpressCheckoutPaymentMethodLauncher.Result.Completed(
                                    io.hyperswitch.payments.expresscheckoutlauncher.PaymentMethod(status, 0, null)
                                )
                            )
                        },
                        defaultErrorCode = ExpressCheckoutPaymentMethodLauncher.INTERNAL_ERROR
                    )
                }
                UnifiedPaymentMethodEnum.CARD -> {
                    handlePaymentResult(
                        status = status,
                        message = message,
                        code = code,
                        onCancelled = { reason ->
                            onCurrentCardResult?.onPaymentResult(PaymentResult.Canceled(reason))
                        },
                        onFailed = { error, _ ->
                            onCurrentCardResult?.onPaymentResult(PaymentResult.Failed(error))
                        },
                        onCompleted = {
                            onCurrentCardResult?.onPaymentResult(PaymentResult.Completed(status))
                        }
                    )
                }
                null -> {
                    System.err.println("WidgetLauncher: Received payment result with unknown type: $paymentMethodTypeStr")
                }
            }
        }

        private fun handlePaymentResult(
            status: String,
            message: String,
            code: String,
            onCancelled: (String) -> Unit,
            onFailed: (Throwable, Int) -> Unit,
            onCompleted: () -> Unit,
            defaultErrorCode: Int = 0
        ) {
            when (status) {
                "cancelled" -> onCancelled(status)
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(message)
                    if (code.isNotEmpty()) throwable.initCause(Throwable(code))
                    onFailed(throwable, defaultErrorCode)
                }
                else -> onCompleted()
            }
        }
    }
}