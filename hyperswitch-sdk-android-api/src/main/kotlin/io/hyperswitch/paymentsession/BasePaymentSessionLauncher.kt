package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet

abstract class BasePaymentSessionLauncher(
    protected val activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?
) : PaymentSessionLauncher {

    protected var sessionToken: String? = null

    init {
        if (publishableKey != null) {
            PaymentConfiguration.init(
                activity.applicationContext,
                publishableKey,
                "",
                customBackendUrl,
                customLogUrl,
                customParams
            )
        }
    }

    override fun initPaymentSession(sessionToken: String) {
        this.sessionToken = sessionToken
    }

    abstract override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        resultCallback: (PaymentResult) -> Unit
    )

    abstract override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentResult) -> Unit
    )

    abstract override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    )
}
