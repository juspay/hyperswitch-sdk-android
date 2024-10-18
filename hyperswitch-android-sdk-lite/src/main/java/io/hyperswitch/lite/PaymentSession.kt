package io.hyperswitch.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import io.hyperswitch.lite.payments.paymentlauncher.PaymentResult
import io.hyperswitch.lite.paymentsheet.PaymentSheet
import io.hyperswitch.lite.paymentsheet.PaymentSheetResult
import io.hyperswitch.lite.react.HyperActivity
import org.json.JSONObject

class PaymentSession {
    constructor(
        activity: Activity,
        publishableKey: String? = null,
        customBackendUrl: String? = null,
        customParams: Bundle? = null,
        customLogUrl: String? = null
    ) {
        init(activity, publishableKey, customBackendUrl, customParams, customLogUrl)
    }

    constructor(
        fragment: Fragment,
        publishableKey: String? = null,
        customBackendUrl: String? = null,
        customParams: Bundle? = null,
        customLogUrl: String? = null
    ) {
        init(
            fragment.requireActivity(),
            publishableKey,
            customBackendUrl,
            customParams,
            customLogUrl
        )
    }

    private fun init(
        activity: Activity,
        publishableKey: String? = null,
        customBackendUrl: String? = null,
        customParams: Bundle? = null,
        customLogUrl: String? = null
    ) {
        try {
            Companion.activity = activity
            if (publishableKey != null) {
                PaymentConfiguration.init(
                    activity.applicationContext,
                    publishableKey,
                    "",
                    customBackendUrl,
                    customParams,
                    customLogUrl
                )
            }
        } catch (ex: IllegalStateException) {
            ex.printStackTrace()
        }
    }

    fun initPaymentSession(
        paymentIntentClientSecret: String
    ) {
        Companion.paymentIntentClientSecret = paymentIntentClientSecret
    }

    fun presentPaymentSheet(resultCallback: (PaymentSheetResult) -> Unit) {
        presentPaymentSheet(null, resultCallback)
    }

    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration? = null,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        isPresented = true
        sheetCompletion = resultCallback
        Companion.configuration = configuration
        activity.startActivity(
            Intent(
                activity.applicationContext,
                HyperActivity::class.java
            ).putExtra("flow", 1)
        )
    }

    fun presentPaymentSheet(map: Map<String, Any?>, resultCallback: (PaymentSheetResult) -> Unit) {
        isPresented = true
        sheetCompletion = resultCallback
        configurationMap = map
        activity.startActivity(
            Intent(
                activity.applicationContext,
                HyperActivity::class.java
            ).putExtra("flow", 2)
        )
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        sheetCompletion?.let { it(paymentSheetResult) }
    }

    @SuppressLint("VisibleForTests")
    fun getCustomerSavedPaymentMethods(
        func: ((PaymentSessionHandler) -> Unit),
    ) {
        isPresented = false
        completion = func
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var activity: Activity

        var paymentIntentClientSecret: String? = null
        var completion: ((PaymentSessionHandler) -> Unit)? = null
        var headlessCompletion: ((PaymentResult) -> Unit)? = null
        var sheetCompletion: ((PaymentSheetResult) -> Unit)? = null
        var configuration: PaymentSheet.Configuration? = null
        var configurationMap: Map<String, Any?>? = null
        var isPresented: Boolean = false


        @SuppressLint("VisibleForTests")
        fun exitHeadless(paymentResult: String) {
            val message = JSONObject(paymentResult)
            when (val status = message.getString("status")) {
                "cancelled" -> headlessCompletion?.let { it(PaymentResult.Canceled(status)) }
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(message.getString("message"))
                    throwable.initCause(Throwable(message.getString("code")))
                    headlessCompletion?.let { it(PaymentResult.Failed(throwable)) }
                }

                else -> headlessCompletion?.let { it(PaymentResult.Completed(status ?: "default")) }
            }
        }
    }
}

sealed class PaymentMethod {
    data class Card(
        val isDefaultPaymentMethod: Boolean,
        val paymentToken: String,
        val cardScheme: String,
        val name: String,
        val expiryDate: String,
        val cardNumber: String,
        val nickName: String,
        val cardHolderName: String,
        val requiresCVV: Boolean,
        val created: String,
        val lastUsedAt: String,
    ) : PaymentMethod() {
        fun toHashMap(): HashMap<String, Any> {
            val hashMap = HashMap<String, Any>()
            hashMap["isDefaultPaymentMethod"] = isDefaultPaymentMethod
            hashMap["paymentToken"] = paymentToken
            hashMap["cardScheme"] = cardScheme
            hashMap["name"] = name
            hashMap["expiryDate"] = expiryDate
            hashMap["cardNumber"] = cardNumber
            hashMap["nickName"] = nickName
            hashMap["cardHolderName"] = cardHolderName
            hashMap["requiresCVV"] = requiresCVV
            hashMap["created"] = created
            hashMap["lastUsedAt"] = lastUsedAt
            return hashMap
        }
    }

    data class Wallet(
        val isDefaultPaymentMethod: Boolean,
        val paymentToken: String,
        val walletType: String,
        val created: String,
        val lastUsedAt: String,
    ) : PaymentMethod() {
        fun toHashMap(): HashMap<String, Any> {
            val hashMap = HashMap<String, Any>()
            hashMap["isDefaultPaymentMethod"] = isDefaultPaymentMethod
            hashMap["paymentToken"] = paymentToken
            hashMap["walletType"] = walletType
            hashMap["created"] = created
            hashMap["lastUsedAt"] = lastUsedAt
            return hashMap
        }
    }

    data class Error(
        val code: String,
        val message: String,
    ) : PaymentMethod() {
        fun toHashMap(): HashMap<String, Any> {
            val hashMap = HashMap<String, Any>()
            hashMap["code"] = code
            hashMap["message"] = message
            return hashMap
        }
    }
}

interface PaymentSessionHandler {
    fun getCustomerDefaultSavedPaymentMethodData(): PaymentMethod
    fun getCustomerLastUsedPaymentMethodData(): PaymentMethod
    fun getCustomerSavedPaymentMethodData(): Array<PaymentMethod>
    fun confirmWithCustomerDefaultPaymentMethod(
        cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )

    fun confirmWithCustomerLastUsedPaymentMethod(
        cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )

    fun confirmWithCustomerPaymentToken(
        paymentToken: String, cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )
}