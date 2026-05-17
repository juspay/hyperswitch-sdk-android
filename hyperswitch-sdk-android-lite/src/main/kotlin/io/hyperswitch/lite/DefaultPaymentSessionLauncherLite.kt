package io.hyperswitch.lite

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentEventSubscriptionBuilder
import android.util.Log
import io.hyperswitch.paymentsession.BasePaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.paymentsession.PresentationInterface
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult

open class DefaultPaymentSessionLauncherLite(
    activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private val webViewUtils: PresentationInterface = WebViewUtils(activity)
) : BasePaymentSessionLauncher(
    activity,
    publishableKey,
    customBackendUrl,
    customLogUrl,
    customParams
) {

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        PaymentSheetCallbackManager.setCallback(resultCallback)
        webViewUtils.presentSheet(sdkAuthorization ?: "", configuration)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        PaymentSheetCallbackManager.setCallback(resultCallback)
        webViewUtils.presentSheet(configurationMap)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
        Log.w(TAG, "getCustomerSavedPaymentMethods is not supported in the lite SDK")
    }

    override suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler =
        throw UnsupportedOperationException(
            "getCustomerSavedPaymentMethods is not supported in the lite SDK"
        )

    companion object {
        private const val TAG = "PaymentSessionLauncherLite"
    }
}