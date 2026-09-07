package io.hyperswitch.paymentsession

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult

interface PaymentSessionLauncher {
    fun initPaymentSession(sessionConfig: PaymentSessionConfiguration)
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?, subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?, resultCallback: (PaymentResult) -> Unit
    )

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>, subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?, resultCallback: (PaymentResult) -> Unit
    )

    suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
    ): PaymentSessionHandler

    fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
        savedPaymentMethodCallback: (PaymentSessionHandler) -> Unit,
    )

    suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler =
        getCustomerSavedPaymentMethods(null)

    fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: (PaymentSessionHandler) -> Unit,
    ) = getCustomerSavedPaymentMethods(null, savedPaymentMethodCallback)
}