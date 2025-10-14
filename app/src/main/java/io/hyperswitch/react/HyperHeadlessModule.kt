package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.Card
import io.hyperswitch.paymentsession.ExitHeadlessCallBackManager
import io.hyperswitch.paymentsession.GetPaymentSessionCallBackManager
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsession.PaymentSessionHandler

class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    override fun getName(): String {
        return "HyperHeadless"
    }

    @ReactMethod
    fun getPaymentSession(
        getPaymentMethodData: ReadableMap,
        getPaymentMethodData2: ReadableMap,
        getPaymentMethodDataArray: ReadableArray,
        callback: Callback
    ) {
        val handler = object : PaymentSessionHandler {
            override fun getCustomerDefaultSavedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData)
            }

            override fun getCustomerLastUsedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData2)
            }

            override fun getCustomerSavedPaymentMethodData(): Array<PaymentMethod> {
                val array = mutableListOf<PaymentMethod>()
                for (i in 0 until getPaymentMethodDataArray.size()) {
                    getPaymentMethodDataArray.getMap(i)?.let {
                        array.add(parseGetPaymentMethodData(it))
                    }
                }
                return array.toTypedArray()
            }

            override fun confirmWithCustomerDefaultPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerLastUsedPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData2.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerPaymentToken(
                paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                try {
                    ExitHeadlessCallBackManager.setCallback(resultHandler)
                    val map = Arguments.createMap()
                    map.putString("paymentToken", paymentToken)
                    map.putString("cvc", cvc)
                    callback.invoke(map)
                } catch (ex: Exception) {
                    val throwable = Throwable("Not Initialised")
                    throwable.initCause(Throwable("Not Initialised"))
                    resultHandler(PaymentResult.Failed(throwable))
                }
            }
        }
        GetPaymentSessionCallBackManager.executeCallback(handler)
    }

    private fun parseGetPaymentMethodData(readableMap: ReadableMap): PaymentMethod {
        val paymentMethod = readableMap.getString("payment_method_str")

        return if (paymentMethod != null) {
            val cardMap = readableMap.getMap("card")

            var card: Card? = null
            if (cardMap != null) {
                card = Card(
                    scheme = cardMap.getString("scheme") ?: "",
                    issuerCountry = cardMap.getString("issuer_country") ?: "",
                    last4Digits = cardMap.getString("last4_digits") ?: "",
                    expiryMonth = cardMap.getString("expiry_month") ?: "",
                    expiryYear = cardMap.getString("expiry_year") ?: "",
                    cardToken = cardMap.getString("card_token"),
                    cardHolderName = cardMap.getString("card_holder_name") ?: "",
                    cardFingerprint = cardMap.getString("card_fingerprint"),
                    nickName = cardMap.getString("nick_name") ?: "",
                    cardNetwork = cardMap.getString("card_network") ?: "",
                    cardIsin = cardMap.getString("card_isin") ?: "",
                    cardIssuer = cardMap.getString("card_issuer") ?: "",
                    cardType = cardMap.getString("card_type") ?: "",
                    savedToLocker = cardMap.getBoolean("saved_to_locker"),
                )
            }

            val paymentExperienceArray = readableMap.getArray("payment_experience")
            val paymentExperienceList = mutableListOf<String>()
            if (paymentExperienceArray != null) {
                for (i in 0 until paymentExperienceArray.size()) {
                    paymentExperienceArray.getString(i)?.let { paymentExperienceList.add(it) }
                }
            }

            PaymentMethod.PaymentMethodType(
                paymentToken = readableMap.getString("payment_token") ?: "",
                paymentMethodId = readableMap.getString("payment_method_id") ?: "",
                customerId = readableMap.getString("customer_id") ?: "",
                paymentMethod = readableMap.getString("payment_method_str") ?: "",
                paymentMethodType = readableMap.getString("payment_method_type") ?: "",
                paymentMethodIssuer = readableMap.getString("payment_method_issuer") ?: "",
                paymentMethodIssuerCode = readableMap.getString("payment_method_issuer_code"),
                recurringEnabled = readableMap.getBoolean("recurring_enabled"),
                installmentPaymentEnabled = readableMap.getBoolean("installment_payment_enabled"),
                paymentExperience = paymentExperienceList,
                card = card,
                metadata = readableMap.getString("metadata"),
                created = readableMap.getString("created") ?: "",
                bank = readableMap.getString("bank"),
                surchargeDetails = readableMap.getString("surcharge_details"),
                requiresCvv = readableMap.getBoolean("requires_cvv"),
                lastUsedAt = readableMap.getString("last_used_at") ?: "",
                defaultPaymentMethodSet = readableMap.getBoolean("default_payment_method_set"),
            )
        } else {
            PaymentMethod.Error(
                code = readableMap.getString("code") ?: "",
                message = readableMap.getString("message") ?: ""
            )
        }
    }

    @ReactMethod
    fun exitHeadless(status: String) {
        ExitHeadlessCallBackManager.executeCallback(status)
    }
}
