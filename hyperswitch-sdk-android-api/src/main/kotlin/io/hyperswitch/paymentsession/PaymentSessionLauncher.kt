package io.hyperswitch.paymentsession

import io.hyperswitch.PaymentEventListener
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

    /** Sheet events listed in `subscriptionEvents` go to [onChange]; launchers without events ignore it. */
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?, onChange: PaymentEventListener, resultCallback: (PaymentResult) -> Unit
    ) = presentPaymentSheet(configuration, subscribe = null, resultCallback)

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>, onChange: PaymentEventListener, resultCallback: (PaymentResult) -> Unit
    ) = presentPaymentSheet(configurationMap, subscribe = null, resultCallback)

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