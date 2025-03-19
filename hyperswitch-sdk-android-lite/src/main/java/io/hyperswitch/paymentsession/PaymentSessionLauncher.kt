package io.hyperswitch.paymentsession

import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import io.hyperswitch.threedslibrary.customization.UiCustomization
import io.hyperswitch.threedslibrary.service.Result
import org.json.JSONObject

interface PaymentSessionLauncher {
    fun initPaymentSession(paymentIntentClientSecret: String)
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?, resultCallback: (PaymentSheetResult) -> Unit
    )

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>, resultCallback: (PaymentSheetResult) -> Unit
    )

    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: (PaymentSessionHandler) -> Unit)

    fun initAuthenticationSession(
        paymentIntentClientSecret: String,
        merchantId: String? = null,
        directoryServerId: String? = null,
        messageVersion: String? = null,
        uiCustomization: UiCustomization? = null,
        tracker: ((JSONObject) -> Unit)? = null,
        initializationCallback: (Result) -> Unit,
    ): AuthenticationSession
}