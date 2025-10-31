package io.hyperswitch.lite

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.paymentsession.BasePaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.paymentsession.SDKInterface
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

open class DefaultPaymentSessionLauncherLite(
    activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private val webViewUtils: SDKInterface = WebViewUtils(activity)
) : BasePaymentSessionLauncher(
    activity,
    publishableKey,
    customBackendUrl,
    customLogUrl,
    customParams
) {

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        PaymentSheetCallbackManager.setCallback(resultCallback)
        webViewUtils.presentSheet(paymentIntentClientSecret ?: "", configuration)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        PaymentSheetCallbackManager.setCallback(resultCallback)
        webViewUtils.presentSheet(configurationMap)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
    }
}