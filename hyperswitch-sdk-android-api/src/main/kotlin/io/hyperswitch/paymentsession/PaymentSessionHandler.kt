package io.hyperswitch.paymentsession

import android.view.View
import io.hyperswitch.paymentsheet.PaymentResult

interface PaymentSessionHandler {
    // Kotlin-facing API — returns Result<T>.
    // NOTE: These are intentionally not annotated with @JvmName because @JvmName
    // is not applicable to interface methods. Java callers should use the
    // callback-style overloads below instead of calling these directly.
    fun getCustomerDefaultSavedPaymentMethodData(): Result<PaymentMethod>
    fun getCustomerLastUsedPaymentMethodData(): Result<PaymentMethod>
    fun getCustomerSavedPaymentMethodData(): Result<List<PaymentMethod>>

    // Java-friendly callback variants.
    // These return Unit (not Result<T>) so the JVM name is never mangled, and
    // Java code can invoke them directly without reflection.
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

    fun confirmWithCustomerPaymentToken(
        paymentToken: String, cvc: String? = null, resultHandler: (PaymentResult) -> Unit
    )

    suspend fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View): PaymentResult
    suspend fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View): PaymentResult

    fun confirmWithCustomerLastUsedPaymentMethod(cvcWidget: View, resultHandler: (PaymentResult) -> Unit)
    fun confirmWithCustomerDefaultPaymentMethod(cvcWidget: View, resultHandler: (PaymentResult) -> Unit)
}
