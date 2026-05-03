package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet

abstract class BasePaymentSessionLauncher(
    protected val activity: Activity,
    config: HyperswitchBaseConfiguration?,
    customParams: Bundle?
) : PaymentSessionLauncher {

    protected var sdkAuthorization: String? = null

    init {
        PaymentConfiguration.init(
            activity.applicationContext,
            config?.publishableKey,
            "",
            config?.customConfig?.overrideCustomBackendEndpoint,
            config?.customConfig?.overrideCustomLoggingEndpoint,
            customParams
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
