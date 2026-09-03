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
    private val settled = AtomicBoolean(false)

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
                onTerminalResult = { _, _ -> },
                initializationError = error,
            )
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
        staleHandlerResult()?.let { return resultHandler(settle(it)) }
        beginConfirmation()?.let { resultHandler(it); return }
        val terminalCallback: ConfirmationCallback = { result -> resultHandler(settle(result)) }
        val entry = HeadlessRegistry.tryRegister(HeadlessRegistry.Kind.CONFIRM, sdkAuthorization, terminalCallback)
        if (entry == null) {
            /* Another confirm for this authorization is in flight. This handler never
               started, so it must stay usable once that one settles. */
            confirmationStarted.set(false)
            resultHandler(alreadyInProgressResult())
            return
        }
        try {
            jsCallback.invoke(Arguments.createMap().apply {
                putString("paymentToken", paymentToken)
                putString("cvc", cvc)
            })
        } catch (ex: Exception) {
            HeadlessRegistry.remove(HeadlessRegistry.Kind.CONFIRM, sdkAuthorization, entry)
            /* The codegen confirm callback is single-shot; a re-invoke lands here. */
            resultHandler(settle(handlerAlreadyUsedResult()))
        }
    }

    // ── CVCWidget suspend overloads ───────────────────────────────────────────

    override suspend fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View): PaymentResult {
        initializationError?.let { return PaymentResult.Failed(it) }
        staleHandlerResult()?.let { return settle(it) }
        beginConfirmation()?.let { return it }
        val method = getCustomerLastUsedPaymentMethodData().getOrElse {
            return settle(PaymentResult.Failed(it))
        }
        val result = (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        } ?: PaymentResult.Failed(Throwable("View can't be cast as CVCWidget"))
        return settle(result)
    }

    override suspend fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View): PaymentResult {
        initializationError?.let { return PaymentResult.Failed(it) }
        staleHandlerResult()?.let { return settle(it) }
        beginConfirmation()?.let { return it }
        val method = getCustomerDefaultSavedPaymentMethodData().getOrElse {
            return settle(PaymentResult.Failed(it))
        }
        val result = (cvcWidget as? CVCWidget)?.let {
            it.setSdkAuthorization(sdkAuthorization)
            it.confirmCVCWidget(sdkAuthorization, method.paymentToken, method.billing)
        } ?: PaymentResult.Failed(Throwable("View can't be cast as CVCWidget"))
        return settle(result)
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
        beginConfirmation()?.let { resultHandler(it); return }
        val result = PaymentResult.Failed(Throwable(message).apply {
            initCause(Throwable("MISSING_PAYMENT_TOKEN"))
        })
        resultHandler(settle(result))
    }

    /* Confirm channels are single-shot on both platforms: the codegen callback is consumed by
       the first confirm and the registry entry by its result. A post-terminal retry on the same
       handler is HANDLER_ALREADY_USED; only an in-flight duplicate is ALREADY_IN_PROGRESS.
       Request a new handler through getCustomerSavedPaymentMethods to confirm again. */
    private fun beginConfirmation(): PaymentResult? =
        when {
            confirmationStarted.compareAndSet(false, true) -> null
            settled.get() -> handlerAlreadyUsedResult()
            else -> alreadyInProgressResult()
        }

    private fun settle(result: PaymentResult): PaymentResult {
        settled.set(true)
        onTerminalResult(sdkAuthorization, result)
        return result
    }

    private fun handlerAlreadyUsedResult(): PaymentResult =
        PaymentResult.Failed(
            Throwable(
                "This saved payment methods handler has already completed; request a new handler"
            ).apply {
                initCause(Throwable("HANDLER_ALREADY_USED"))
            }
        )

    /* Headless confirms cannot be JS-guarded: the headless root's nativeProp is frozen at
       launch (its clientSecret is used verbatim at confirm time), so only native — which owns
       the current authorization — can reject a confirm from a superseded-intent handler. */
    private fun staleHandlerResult(): PaymentResult.Failed? =
        if (currentSdkAuthorization() == sdkAuthorization) {
            null
        } else {
            PaymentResult.Failed(
                Throwable(
                    "Saved payment methods handler belongs to a previous payment intent; call "
                        + "getCustomerSavedPaymentMethods again"
                ).apply {
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
