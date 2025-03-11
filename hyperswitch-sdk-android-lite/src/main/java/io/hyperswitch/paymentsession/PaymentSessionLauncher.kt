package io.hyperswitch.paymentsession

import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

interface PaymentSessionLauncher {
    fun initPaymentSession(paymentIntentClientSecret: String)

    fun initAuthenticationSession(paymentIntentClientSecret: String): AuthenticationSession

    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?, resultCallback: (PaymentSheetResult) -> Unit
    )

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>, resultCallback: (PaymentSheetResult) -> Unit
    )

    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: (PaymentSessionHandler) -> Unit)
}