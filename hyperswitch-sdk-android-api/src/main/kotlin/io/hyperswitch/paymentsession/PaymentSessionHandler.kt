package io.hyperswitch.paymentsession

import android.view.View
import io.hyperswitch.paymentsheet.PaymentResult

interface PaymentSessionHandler {
    // -------------------------------------------------------------------------
    // Kotlin-only API — DO NOT call from Java.
    // -------------------------------------------------------------------------
    fun getCustomerDefaultSavedPaymentMethodData(): Result<PaymentMethod>
    fun getCustomerLastUsedPaymentMethodData(): Result<PaymentMethod>
    fun getCustomerSavedPaymentMethodData(): Result<List<PaymentMethod>>

    // -------------------------------------------------------------------------
    // Java-friendly callback variants.
    // -------------------------------------------------------------------------
    fun getCustomerDefaultSavedPaymentMethodData(
        onSuccess: (PaymentMethod) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        getCustomerDefaultSavedPaymentMethodData()
            .onSuccess(onSuccess)
            .onFailure(onFailure)
    }

    fun getCustomerLastUsedPaymentMethodData(
        onSuccess: (PaymentMethod) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        getCustomerLastUsedPaymentMethodData()
            .onSuccess(onSuccess)
            .onFailure(onFailure)
    }

    fun getCustomerSavedPaymentMethodData(
        onSuccess: (List<PaymentMethod>) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        getCustomerSavedPaymentMethodData()
            .onSuccess(onSuccess)
            .onFailure(onFailure)
    }

    fun confirmWithCustomerDefaultPaymentMethod(
        cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )

    fun confirmWithCustomerLastUsedPaymentMethod(
        cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )
    fun updateSdkAuthorization(sdkAuthorization : String)

    fun confirmWithCustomerPaymentToken(
        paymentToken: String, cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )

    // -------------------------------------------------------------------------
    // Kotlin-only — hidden Continuation<T> parameter; not callable from Java.
    // -------------------------------------------------------------------------
    suspend fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View): PaymentResult
    suspend fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View): PaymentResult

    // -------------------------------------------------------------------------
    // Java-friendly callback variants — no Continuation, no name mangling.
    // -------------------------------------------------------------------------
    fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View, resultHandler: (PaymentResult) -> Unit)
    fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View, resultHandler: (PaymentResult) -> Unit)
}
