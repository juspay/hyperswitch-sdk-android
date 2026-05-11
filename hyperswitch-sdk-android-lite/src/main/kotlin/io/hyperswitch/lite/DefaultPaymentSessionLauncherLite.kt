package io.hyperswitch.lite

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.paymentsession.BasePaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.paymentsession.SDKInterface
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult

open class DefaultPaymentSessionLauncherLite(
    activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private val webViewUtils: SDKInterface = WebViewUtils(
        activity, HyperswitchConfiguration(
            publishableKey = publishableKey,
            customConfig = CustomEndpointConfiguration.OverrideEndpoints(
                loggingEndpoint = customLogUrl,
                backendEndpoint = customBackendUrl
            )
        )
    )
) : BasePaymentSessionLauncher(
    activity,
    HyperswitchConfiguration(
        publishableKey = publishableKey,
        customConfig = CustomEndpointConfiguration.OverrideEndpoints(
            loggingEndpoint = customLogUrl,
            backendEndpoint = customBackendUrl
        )
    ),
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
    }

    override suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler {
        TODO("Not yet implemented")
    }
}