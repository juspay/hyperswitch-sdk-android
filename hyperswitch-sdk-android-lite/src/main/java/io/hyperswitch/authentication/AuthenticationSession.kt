package io.hyperswitch.authentication

import android.app.Activity
import android.content.Context
import io.hyperswitch.threedslibrary.core.Transaction
import io.hyperswitch.threedslibrary.customization.ButtonCustomization
import io.hyperswitch.threedslibrary.customization.CancelDialogCustomization
import io.hyperswitch.threedslibrary.customization.FontStyle
import io.hyperswitch.threedslibrary.customization.ToolbarCustomization
import `in`.juspay.trident.core.Transaction as TridentTransaction
import io.hyperswitch.threedslibrary.customization.UiCustomization
import io.hyperswitch.threedslibrary.data.AuthenticationRequestParameters
import io.hyperswitch.threedslibrary.data.ChallengeParameters
import io.hyperswitch.threedslibrary.data.ThreeDSAuthParameters
import io.hyperswitch.threedslibrary.di.ThreeDSFactory
import io.hyperswitch.threedslibrary.di.ThreeDSSDKType
import io.hyperswitch.threedslibrary.service.Result
import io.hyperswitch.threedslibrary.threeDSSDKAdapters.trident.TridentSDK
import org.json.JSONObject

object AuthenticationSession {

    private lateinit var threeDSInstance: TridentSDK
    lateinit var publishableKey: String
    lateinit var transaction: Transaction<TridentTransaction>

    fun setAuthSessionPublishableKey(publishableKey: String) {
        this.publishableKey = publishableKey
    }

    fun init(
        context: Context,
        paymentIntentClientSecret: String,
        merchantId: String? = null,
        directoryServerId: String? = null,
        messageVersion: String? = null,
        uiCustomization: UiCustomization? = null,
        tracker: ((JSONObject) -> Unit)?,
        initializationCallback: (Result) -> Unit,
    ): AuthenticationSession {

        val directFlow = merchantId != null && directoryServerId != null && messageVersion != null
        var threeDSParams: ThreeDSAuthParameters? = null

        if (directFlow) {
            val paymentId = paymentIntentClientSecret.substringBefore("_secret_")

            threeDSParams = ThreeDSAuthParameters(
                clientSecret = paymentIntentClientSecret,
                paymentId = paymentId,
                messageVersion = messageVersion!!,
                directoryServerID = directoryServerId!!,
                merchantId = merchantId!!,
            )
            ThreeDSFactory.initialize<TridentSDK>(
                ThreeDSSDKType.TRIDENT,
                threeDSParams,
                publishableKey
            )
        } else {
            ThreeDSFactory.initialize<TridentSDK>(
                ThreeDSSDKType.TRIDENT,
                paymentIntentClientSecret,
                publishableKey
            )
        }

        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()
        if(directFlow && threeDSParams != null) {
            threeDSInstance.setAuthenticationResponse(threeDSParams)
        } else {
            threeDSInstance.setClientSecret(paymentIntentClientSecret)
        }

        val defaultCustomization = UiCustomization(
            submitButtonCustomization = ButtonCustomization(
                backgroundColor = "#356fd3"
            ),
            resendButtonCustomization = ButtonCustomization(
                textColor = "#356fd3",
                backgroundColor = "#FFFFFF",
                fontStyle = FontStyle.REGULAR
            ),
            toolbarCustomization = ToolbarCustomization(
                backgroundColor = "#356fd3"
            ),
            cancelDialogCustomization = CancelDialogCustomization(
                continueButtonCustomization = ButtonCustomization(
                    backgroundColor = "#356fd3"
                )
            ),
            showJpBrandingFooter = true,
            screenHorizontalPadding = 16,
            screenVerticalPadding = 8,
            showExpandableInfoTexts = true
        )

        threeDSInstance.initialise(
            context,
            "en-US",
            uiCustomization ?: defaultCustomization,
            tracker,
            initializationCallback
        )

        return this
    }

    fun startAuthentication(activity: Activity, completionCallback: (Result) -> Unit) {
        threeDSInstance.startAuthentication(activity, completionCallback)
    }

    fun createTransaction(
        directoryServerID: String,
        messageVersion: String
    ):  Transaction<TridentTransaction> {

        this.transaction =
            threeDSInstance.createTransaction(directoryServerID, messageVersion)
        return transaction;
    }

    fun getAuthenticationRequestParameters(): AuthenticationRequestParameters {
        return transaction.getAuthenticationRequestParameters()
    }

    fun getChallengeParameters(aReq: AuthenticationRequestParameters): ChallengeParameters {
        return threeDSInstance.getChallengeParameters(aReq)
    }


    fun getMessageVersion(): String {
        return threeDSInstance.getMessageVersion()
    }

    fun getDirectoryServerID(): String {
        return threeDSInstance.getDirectoryServerID()
    }

}