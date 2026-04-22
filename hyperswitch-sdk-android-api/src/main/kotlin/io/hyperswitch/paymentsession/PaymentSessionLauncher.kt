package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult

interface PaymentSessionLauncher {
    fun initPaymentSession(sdkAuthorization: String)
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?, resultCallback: (PaymentResult) -> Unit
    )

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>, resultCallback: (PaymentResult) -> Unit
    )

    suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler

    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: (PaymentSessionHandler) -> Unit)
}