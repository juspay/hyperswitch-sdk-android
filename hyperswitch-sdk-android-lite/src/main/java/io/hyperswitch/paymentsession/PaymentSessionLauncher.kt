package io.hyperswitch.paymentsession

import io.hyperswitch.authentication.AuthenticationResponse
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.authentication.PaymentIntentClientSecret
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
        uiCustomization: UiCustomization? = null,
        tracker: ((JSONObject) -> Unit)? = null,
        initializationCallback: (Result) -> Unit,

        ): AuthenticationSession

    fun initAuthenticationSession(
        paymentIntentClientSecret: String,
        merchantId:String,
        directoryServerId:String,
        messageVersion:String,
        uiCustomization: UiCustomization? = null,
        tracker: ((JSONObject) -> Unit)? = null,
        initializationCallback: (Result) -> Unit,
    ): AuthenticationSession

}