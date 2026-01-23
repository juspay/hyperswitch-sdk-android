package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

class DefaultPaymentSessionLauncher(
    activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private var paymentSessionReactLauncher: SDKInterface = PaymentSessionReactLauncher(activity)
) : BasePaymentSessionLauncher(
    activity,
    publishableKey,
    customBackendUrl,
    customLogUrl,
    customParams
) {

    init {
        if (publishableKey != null) {
            val loggingEndPoint = if (customLogUrl != "" && customLogUrl != null) {
                customLogUrl
            } else {
                getLoggingUrl(publishableKey)
            }
            HyperLogManager.initialise(publishableKey, loggingEndPoint)
            HyperLogManager.sendLogsFromFile(LogFileManager(activity))
        }
        paymentSessionReactLauncher.initializeReactNativeInstance()
    }

    override fun initPaymentSession(paymentIntentClientSecret: String) {
        super.initPaymentSession(paymentIntentClientSecret)
        Companion.paymentIntentClientSecret = paymentIntentClientSecret
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        isPresented = true
        val isFragment =
            paymentSessionReactLauncher.presentSheet(paymentIntentClientSecret ?: "", configuration)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        isPresented = true
        val isFragment = paymentSessionReactLauncher.presentSheet(configurationMap)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
        isPresented = false
        GetPaymentSessionCallBackManager.setCallback(savedPaymentMethodCallback)
        paymentSessionReactLauncher.recreateReactContext()
    }

    companion object {
        var isPresented: Boolean = false
        var paymentIntentClientSecret: String? = null
    }
}