package io.hyperswitch.paymentsession

import android.view.View
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.utils.ConversionUtils
import io.hyperswitch.view.CVCWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class PaymentSessionHandlerImpl(
    private val attempt: HeadlessAttempt,
    private val defaultMethodData: ReadableMap,
    private val lastUsedMethodData: ReadableMap,
    private val allMethodsData: ReadableArray,
) : PaymentSessionHandler {

    private val sdkAuthorization: String
        get() = attempt.sdkAuthorization

    override fun getCustomerDefaultSavedPaymentMethodData(): Result<PaymentMethod> =
        parsePaymentMethod(defaultMethodData)

    override fun getCustomerLastUsedPaymentMethodData(): Result<PaymentMethod> =
        parsePaymentMethod(lastUsedMethodData)

    override fun getCustomerSavedPaymentMethodData(): Result<List<PaymentMethod>> {
        val list = mutableListOf<PaymentMethod>()
        for (i in 0 until allMethodsData.size()) {
            allMethodsData.getMap(i)?.let { map ->
                parsePaymentMethod(map).onSuccess { list.add(it) }
            }
        }
        return Result.success(list)
    }

    override fun confirmWithCustomerDefaultPaymentMethod(
        cvc: String?, resultHandler: (PaymentResult) -> Unit
    ) {
        confirm(defaultMethodData.getString("payment_token"), cvc, resultHandler)
    }

    override fun confirmWithCustomerLastUsedPaymentMethod(
        cvc: String?, resultHandler: (PaymentResult) -> Unit
    ) {
        confirm(lastUsedMethodData.getString("payment_token"), cvc, resultHandler)
    }

    /** A method without a token (no saved method) fails at once instead of never answering. */
    private fun confirm(token: String?, cvc: String?, resultHandler: (PaymentResult) -> Unit) {
        if (token == null) {
            resultHandler(PaymentResult.Failed(Throwable("The selected payment method has no payment token.").apply {
                initCause(Throwable("NO_PAYMENT_TOKEN"))
            }))
            return
        }
        confirmWithCustomerPaymentToken(token, cvc, resultHandler)
    }

    override fun confirmWithCustomerPaymentToken(
        paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit
    ) {
        attempt.confirm(paymentToken, cvc, resultHandler)
    }

    // ── CVCWidget suspend overloads ───────────────────────────────────────────

    override suspend fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View): PaymentResult {
        if (attempt.isUpdating()) return updateInProgress()
        val method = getCustomerLastUsedPaymentMethodData()
            .getOrElse { return PaymentResult.Failed(it) }
        (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            return it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        }
        return notACvcWidget()
    }

    override suspend fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View): PaymentResult {
        if (attempt.isUpdating()) return updateInProgress()
        val method = getCustomerDefaultSavedPaymentMethodData()
            .getOrElse { return PaymentResult.Failed(it) }
        (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            return it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        }
        return notACvcWidget()
    }

    private fun notACvcWidget(): PaymentResult = PaymentResult.Failed(
        Throwable("View can't be cast as CVCWidget").apply { initCause(Throwable("WIDGET_UNAVAILABLE")) }
    )

    private fun updateInProgress(): PaymentResult = PaymentResult.Failed(
        Throwable("An intent update is in progress; confirm after it completes").apply {
            initCause(Throwable("UPDATE_IN_PROGRESS"))
        }
    )

    // ── CVCWidget callback overloads (Java-friendly, no Continuation needed) ─

    override fun confirmWithCustomerLastUsedPaymentMethod(
        cvcWidget: View, resultHandler: (PaymentResult) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            resultHandler(confirmWithCustomerLastUsedPaymentMethod(cvcWidget))
        }
    }

    override fun confirmWithCustomerDefaultPaymentMethod(
        cvcWidget: View, resultHandler: (PaymentResult) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            resultHandler(confirmWithCustomerDefaultPaymentMethod(cvcWidget))
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun parsePaymentMethod(map: ReadableMap): Result<PaymentMethod> {
        val paymentMethodStr = map.getString("payment_method_str")
            ?: return Result.failure(PMError(
                code = map.getString("code") ?: "",
                message = map.getString("message") ?: ""
            ))

        val cardMap = map.getMap("card")
        val card = cardMap?.let {
            Card(
                scheme = it.getString("scheme") ?: "",
                issuerCountry = it.getString("issuer_country") ?: "",
                last4Digits = it.getString("last4_digits") ?: "",
                expiryMonth = it.getString("expiry_month") ?: "",
                expiryYear = it.getString("expiry_year") ?: "",
                cardToken = it.getString("card_token"),
                cardHolderName = it.getString("card_holder_name") ?: "",
                cardFingerprint = it.getString("card_fingerprint"),
                nickName = it.getString("nick_name") ?: "",
                cardNetwork = it.getString("card_network") ?: "",
                cardIsin = it.getString("card_isin") ?: "",
                cardIssuer = it.getString("card_issuer") ?: "",
                cardType = it.getString("card_type") ?: "",
                savedToLocker = it.getBoolean("saved_to_locker"),
            )
        }

        val paymentExperience = buildList {
            val arr = map.getArray("payment_experience") ?: return@buildList
            for (i in 0 until arr.size()) arr.getString(i)?.let { add(it) }
        }

        return Result.success(PaymentMethod(
            paymentToken = map.getString("payment_token") ?: "",
            paymentMethodId = map.getString("payment_method_id") ?: "",
            customerId = map.getString("customer_id") ?: "",
            paymentMethod = PaymentMethodType.fromString(paymentMethodStr),
            paymentMethodType = map.getString("payment_method_type") ?: "",
            paymentMethodIssuer = map.getString("payment_method_issuer") ?: "",
            paymentMethodIssuerCode = map.getString("payment_method_issuer_code"),
            recurringEnabled = map.getBoolean("recurring_enabled"),
            installmentPaymentEnabled = map.getBoolean("installment_payment_enabled"),
            paymentExperience = paymentExperience,
            card = card,
            metadata = map.getString("metadata"),
            created = map.getString("created") ?: "",
            bank = map.getString("bank"),
            surchargeDetails = map.getString("surcharge_details"),
            requiresCvv = map.getBoolean("requires_cvv"),
            lastUsedAt = map.getString("last_used_at") ?: "",
            defaultPaymentMethodSet = map.getBoolean("default_payment_method_set"),
            billing = map.getMap("billing")?.let { ConversionUtils.convertMapToJson(it).toString() },
        ))
    }
}
