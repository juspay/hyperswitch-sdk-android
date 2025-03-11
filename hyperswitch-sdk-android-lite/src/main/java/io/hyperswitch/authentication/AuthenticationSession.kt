package io.hyperswitch.authentication

import android.app.Activity
import android.app.Application
import `in`.juspay.trident.core.ConfigParameters
import `in`.juspay.trident.core.Transaction
import `in`.juspay.trident.customization.ButtonCustomization
import `in`.juspay.trident.customization.CancelDialogCustomization
import `in`.juspay.trident.customization.FontCustomization
import `in`.juspay.trident.customization.FontStyle
import `in`.juspay.trident.customization.LabelCustomization
import `in`.juspay.trident.customization.LoaderCustomization
import `in`.juspay.trident.customization.OTPSheetCustomization
import `in`.juspay.trident.customization.TextBoxCustomization
import `in`.juspay.trident.customization.ToolbarCustomization
import `in`.juspay.trident.customization.UiCustomization
import `in`.juspay.trident.data.AuthenticationRequestParameters
import `in`.juspay.trident.data.ChallengeParameters
import `in`.juspay.trident.data.ChallengeStatusReceiver
import `in`.juspay.trident.data.CompletionEvent
import `in`.juspay.trident.data.ErrorMessage
import `in`.juspay.trident.data.ProtocolErrorEvent
import `in`.juspay.trident.data.RuntimeErrorEvent
import io.hyperswitch.threedslibrary.authenticationSDKs.TridentSDK
import io.hyperswitch.threedslibrary.di.ThreeDSFactory
import io.hyperswitch.threedslibrary.di.ThreeDSSDKType



object AuthenticationSession {

    lateinit var threeDSInstance: TridentSDK
    lateinit var applicationContext: Application
    fun init(
        applicationContext: Application,
        publishableKey: String,
        clientSecret: String
    ): AuthenticationSession {
        AuthenticationSession.applicationContext = applicationContext
        ThreeDSFactory.initialize<TridentSDK>(
            ThreeDSSDKType.TRIDENT,
            clientSecret,
            "pk_snd_23ff7c6d50e5424ba2e88415772380cd",
            UiCustomization(
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
                labelCustomization = LabelCustomization(),
                textBoxCustomization = TextBoxCustomization(),
                loaderCustomization = LoaderCustomization(),
                fontCustomization = FontCustomization(),
                otpSheetCustomization = OTPSheetCustomization(),
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
        )
        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()
        threeDSInstance.setClientSecret(clientSecret)
        threeDSInstance.initialise(
            applicationContext, ConfigParameters(), "en-US",
            null
        )

        return this

    }

    fun startAuthentication(activity: Activity, challengeStatusReceiver: ChallengeStatusReceiver) {

        threeDSInstance.startAuthentication(applicationContext, activity, challengeStatusReceiver)
    }

    fun createTransaction(
        directoryServerID: String,
        messageVersion: String
    ): Transaction {
        return threeDSInstance.createTransaction(directoryServerID, messageVersion)
    }

    fun getAuthenticationRequestParameters(): AuthenticationRequestParameters {
        return threeDSInstance.getAuthenticationRequestParameters()
    }

    fun getChallengeParameters(aReq: AuthenticationRequestParameters): ChallengeParameters {
        return threeDSInstance.getChallengeParameters(aReq)
    }


    fun doChallenge(
        activity: Activity,
        challengeParameters: ChallengeParameters,
        challengeStatusReceiver: ChallengeStatusReceiver,
        timeOutInMinutes: Int,
        bankDetails: String? = "{}"
    ) {
        threeDSInstance.doChallenge(
            activity,
            challengeParameters,
            challengeStatusReceiver,
            timeOutInMinutes,
            bankDetails
        )
    }

    fun getMessageVersion(): String {
        return threeDSInstance.getMessageVersion()
    }

    fun getDirectoryServerID(): String {
        return threeDSInstance.getDirectoryServerID()
    }

}