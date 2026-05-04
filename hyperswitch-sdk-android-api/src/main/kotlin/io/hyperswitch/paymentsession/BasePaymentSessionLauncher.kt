package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet

abstract class BasePaymentSessionLauncher(
    protected val activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    profileId: String? = null,
) : PaymentSessionLauncher {

    protected var sdkAuthorization: String? = null

    init {
        PaymentConfiguration.init(
            context = activity.applicationContext,
            publishableKey = publishableKey,
            stripeAccountId = "",
            customBackendUrl = customBackendUrl,
            customLogUrl = customLogUrl,
            customParams = customParams,
            profileId = profileId,
        )
    }

    override fun initPaymentSession(sdkAuthorization: String) {
        this.sdkAuthorization = sdkAuthorization
    }

    abstract override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    )

    abstract override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    )

    abstract override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    )

    abstract override suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler
}
