package io.hyperswitch.paymentsession

import android.app.Activity
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet

abstract class BasePaymentSessionLauncher(
    protected val activity: Activity,
    @get:JvmName("hsConfigInternal")
    protected val hsConfig: HyperswitchBaseConfiguration?,
) : PaymentSessionLauncher {

    protected var sessionConfig: PaymentSessionConfiguration? = null

    init {
        PaymentConfiguration.init(
            activity.applicationContext,
            hsConfig?.publishableKey,
            hsConfig?.profileId,
        )
    }

    override fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        this.sessionConfig = sessionConfig
    }

    fun getHsConfig(): HyperswitchBaseConfiguration? = hsConfig

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
        configuration: SavedPaymentMethodsConfiguration?,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    )

    abstract override suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
    ): PaymentSessionHandler
}
