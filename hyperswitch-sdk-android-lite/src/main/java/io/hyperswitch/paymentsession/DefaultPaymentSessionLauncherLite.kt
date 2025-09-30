package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.lite.WebViewUtils
import io.hyperswitch.lite.WebViewWarmUpHelper
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

open class DefaultPaymentSessionLauncherLite(
    private val activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private val webViewUtils: SDKInterface = WebViewUtils(activity)
) : PaymentSessionLauncher {

    protected var paymentIntentClientSecret: String? = null

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
        
        // Early WebView warm-up to prevent Resources$NotFoundException crashes
        // This happens before WebView preloading, ensuring WebViewFactory is initialized safely
        WebViewWarmUpHelper.warmUpWhenIdle(activity)
    }

    // Method to initialize the payment session
    override fun initPaymentSession(paymentIntentClientSecret: String) {
        this.paymentIntentClientSecret = paymentIntentClientSecret
    }

    // Method to present the payment sheet with configuration
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

    // Get customer saved payment methods
    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
    }
}
