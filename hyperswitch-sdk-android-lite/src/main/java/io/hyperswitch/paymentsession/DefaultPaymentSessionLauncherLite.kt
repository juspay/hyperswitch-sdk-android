package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.lite.WebViewUtils
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import io.hyperswitch.threedslibrary.customization.UiCustomization
import io.hyperswitch.threedslibrary.service.Result
import org.json.JSONObject

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
            AuthenticationSession.setAuthSessionPublishableKey(publishableKey)
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

    override fun initAuthenticationSession(
        paymentIntentClientSecret: String,
        merchantId: String?,
        directoryServerId: String?,
        messageVersion: String?,
        uiCustomization: UiCustomization?,
        tracker: ((JSONObject) -> Unit)?,
        initializationCallback: (Result) -> Unit,
    ): AuthenticationSession {

        return AuthenticationSession.init(
            activity,
            paymentIntentClientSecret,
            merchantId,
            directoryServerId,
            messageVersion,
            uiCustomization,
            tracker,
            initializationCallback,
        )
    }


}
