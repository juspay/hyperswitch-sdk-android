package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

abstract class BasePaymentSessionLauncher(
    protected val activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?
) : PaymentSessionLauncher {

    protected var paymentIntentClientSecret: String? = null

    init {
        if (publishableKey != null) {
            // Preserve existing sdkAuthorization if present
            val existingSdkAuth = try {
                PaymentConfiguration.getInstance(activity.applicationContext).sdkAuthorization
            } catch (e: IllegalStateException) {
                null
            }
            
            PaymentConfiguration.init(
                activity.applicationContext,
                publishableKey,
                "",
                customBackendUrl,
                customLogUrl,
                customParams,
                existingSdkAuth
            )
        }
    }

    override fun initPaymentSession(paymentIntentClientSecret: String) {
        this.paymentIntentClientSecret = paymentIntentClientSecret
    }

    abstract override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        resultCallback: (PaymentSheetResult) -> Unit
    )

    abstract override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    )

    abstract override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    )
}
