package io.hyperswitch.paymentsession

import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.utils.ConversionUtils
import io.hyperswitch.view.CVCWidget
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

internal class PaymentSessionHandlerImpl(
    private val sdkAuthorization: String,
    private val currentSdkAuthorization: () -> String,
    private val defaultMethodData: ReadableMap,
    private val lastUsedMethodData: ReadableMap,
    private val allMethodsData: ReadableArray,
    private val jsCallback: Callback,
    private val onTerminalResult: (String, PaymentResult) -> Unit,
    private val initializationError: Throwable? = null,
) : PaymentSessionHandler {

    private val confirmationStarted = AtomicBoolean(false)

    internal companion object {
        fun failed(error: Throwable): PaymentSessionHandlerImpl {
            val errorMap = Arguments.createMap().apply {
                putString("code", error.cause?.message ?: "UNKNOWN_ERROR")
                putString("message", error.message ?: "Unable to load saved payment methods")
            }
            return PaymentSessionHandlerImpl(
                sdkAuthorization = "",
                currentSdkAuthorization = { "" },
                defaultMethodData = errorMap,
                lastUsedMethodData = errorMap,
                allMethodsData = Arguments.createArray(),
                jsCallback = Callback {},
                // A failed handler never reaches the registry-dependent paths
                // (initializationError short-circuits first).
                onTerminalResult = { _, _ -> },
                initializationError = error,
            )
        }

        fun stale(): PaymentSessionHandlerImpl = failed(staleHandlerError())

        private fun staleHandlerError(): Throwable =
            Throwable("Saved payment methods handler belongs to the previous payment intent").apply {
                initCause(Throwable("STALE_PAYMENT_SESSION_HANDLER"))
            }
    }

    override fun getCustomerDefaultSavedPaymentMethodData(): Result<PaymentMethod> {
        initializationError?.let { return Result.failure(it) }
        return parsePaymentMethod(defaultMethodData)
    }

    override fun getCustomerLastUsedPaymentMethodData(): Result<PaymentMethod> {
        initializationError?.let { return Result.failure(it) }
        return parsePaymentMethod(lastUsedMethodData)
    }

    override fun getCustomerSavedPaymentMethodData(): Result<List<PaymentMethod>> {
        initializationError?.let { return Result.failure(it) }
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
        initializationError?.let { error ->
            resultHandler(PaymentResult.Failed(error))
            return
        }
        val paymentToken = defaultMethodData.getString("payment_token")
        if (paymentToken == null) {
            deliverDirectFailure("Saved payment method has no payment token", resultHandler)
        } else {
            confirmWithCustomerPaymentToken(paymentToken, cvc, resultHandler)
        }
    }

    override fun confirmWithCustomerLastUsedPaymentMethod(
        cvc: String?, resultHandler: (PaymentResult) -> Unit
    ) {
        initializationError?.let { error ->
            resultHandler(PaymentResult.Failed(error))
            return
        }
        val paymentToken = lastUsedMethodData.getString("payment_token")
        if (paymentToken == null) {
            deliverDirectFailure("Saved payment method has no payment token", resultHandler)
        } else {
            confirmWithCustomerPaymentToken(paymentToken, cvc, resultHandler)
        }
    }

    override fun confirmWithCustomerPaymentToken(
        paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit
    ) {
        initializationError?.let { error ->
            resultHandler(PaymentResult.Failed(error))
            return
        }
        staleHandlerResult()?.let { result ->
            onTerminalResult(sdkAuthorization, result)
            resultHandler(result)
            return
        }
        if (!confirmationStarted.compareAndSet(false, true)) {
            resultHandler(alreadyInProgressResult())
            return
        }
        try {
            val terminalCallback = { result: PaymentResult ->
                onTerminalResult(sdkAuthorization, result)
                resultHandler(result)
            }
            val registered = SavedMethodConfirmationRegistry.tryRegister(
                sdkAuthorization = sdkAuthorization,
                callback = terminalCallback,
            )
            if (!registered) {
                resultHandler(alreadyInProgressResult())
                return
            }
            jsCallback.invoke(Arguments.createMap().apply {
                putString("paymentToken", paymentToken)
                putString("cvc", cvc)
            })
        } catch (ex: Exception) {
            SavedMethodConfirmationRegistry.remove(sdkAuthorization)
            val result = PaymentResult.Failed(Throwable("Not Initialised").apply {
                initCause(Throwable("Not Initialised"))
            })
            onTerminalResult(sdkAuthorization, result)
            resultHandler(result)
        }
    }

    // ── CVCWidget suspend overloads ───────────────────────────────────────────

    override suspend fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View): PaymentResult {
        initializationError?.let { return PaymentResult.Failed(it) }
        staleHandlerResult()?.let { result ->
            onTerminalResult(sdkAuthorization, result)
            return result
        }
        if (!confirmationStarted.compareAndSet(false, true)) {
            return alreadyInProgressResult()
        }
        val method = getCustomerLastUsedPaymentMethodData().getOrElse {
            return PaymentResult.Failed(it).also {
                onTerminalResult(sdkAuthorization, it)
            }
        }
        val result = (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        } ?: PaymentResult.Failed(Throwable("View can't be cast as CVCWidget"))
        onTerminalResult(sdkAuthorization, result)
        return result
    }

    override suspend fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View): PaymentResult {
        initializationError?.let { return PaymentResult.Failed(it) }
        staleHandlerResult()?.let { result ->
            onTerminalResult(sdkAuthorization, result)
            return result
        }
        if (!confirmationStarted.compareAndSet(false, true)) {
            return alreadyInProgressResult()
        }
        val method = getCustomerDefaultSavedPaymentMethodData().getOrElse {
            return PaymentResult.Failed(it).also {
                onTerminalResult(sdkAuthorization, it)
            }
        }
        val result = (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        } ?: PaymentResult.Failed(Throwable("View can't be cast as CVCWidget"))
        onTerminalResult(sdkAuthorization, result)
        return result
    }

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

    private fun deliverDirectFailure(
        message: String,
        resultHandler: (PaymentResult) -> Unit,
    ) {
        staleHandlerResult()?.let { result ->
            onTerminalResult(sdkAuthorization, result)
            resultHandler(result)
            return
        }
        if (!confirmationStarted.compareAndSet(false, true)) {
            resultHandler(alreadyInProgressResult())
            return
        }
        val result = PaymentResult.Failed(Throwable(message).apply {
            initCause(Throwable("MISSING_PAYMENT_TOKEN"))
        })
        onTerminalResult(sdkAuthorization, result)
        resultHandler(result)
    }

    private fun staleHandlerResult(): PaymentResult.Failed? =
        if (currentSdkAuthorization() == sdkAuthorization) {
            null
        } else {
            PaymentResult.Failed(
                Throwable("Saved payment methods handler belongs to the previous payment intent").apply {
                    initCause(Throwable("STALE_PAYMENT_SESSION_HANDLER"))
                }
            )
        }

    private fun alreadyInProgressResult(): PaymentResult =
        PaymentResult.Failed(
            Throwable("Payment confirmation already in progress for this handler").apply {
                initCause(Throwable("ALREADY_IN_PROGRESS"))
            }
        )

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
