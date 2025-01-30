package io.hyperswitch.paymentsession

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

class DefaultPaymentSessionLauncher(
    activity: Activity,
    publishableKey: String?,
    customBackendUrl: String?,
    customLogUrl: String?,
    customParams: Bundle?,
    private var reactNativeUtils: SDKInterface = ReactNativeUtils(activity)
) : DefaultPaymentSessionLauncherLite(
    activity,
    publishableKey,
    customBackendUrl,
    customLogUrl,
    customParams
) {

    init {
        if (publishableKey != null) {
            HyperLogManager.initialise(activity,publishableKey)
            val fileManager = LogFileManager(activity)
            HyperLogManager.sendLogsFromFile(fileManager)
        }
        reactNativeUtils.initializeReactNativeInstance()
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
            reactNativeUtils.presentSheet(paymentIntentClientSecret ?: "", configuration)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        isPresented = true
        val isFragment = reactNativeUtils.presentSheet(configurationMap)
        PaymentSheetCallbackManager.setCallback(resultCallback, isFragment)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)
    ) {
        isPresented = false
        GetPaymentSessionCallBackManager.setCallback(savedPaymentMethodCallback)
        reactNativeUtils.recreateReactContext()
    }

    companion object {
        var isPresented: Boolean = false
        var paymentIntentClientSecret: String? = null
    }
}