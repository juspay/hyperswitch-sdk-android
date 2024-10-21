package io.hyperswitch.paymentsession

import io.hyperswitch.payments.paymentlauncher.PaymentResult

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