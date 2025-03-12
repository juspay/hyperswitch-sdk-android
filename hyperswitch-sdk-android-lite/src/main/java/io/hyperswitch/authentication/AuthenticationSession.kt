package io.hyperswitch.authentication

import android.app.Activity
import android.app.Application
import io.hyperswitch.threedslibrary.authenticationSDKs.TridentSDK
import io.hyperswitch.threedslibrary.core.Transaction
import io.hyperswitch.threedslibrary.customization.UiCustomization
import io.hyperswitch.threedslibrary.data.AuthenticationRequestParameters
import io.hyperswitch.threedslibrary.data.ChallengeParameters
import io.hyperswitch.threedslibrary.di.ThreeDSFactory
import io.hyperswitch.threedslibrary.di.ThreeDSSDKType
import io.hyperswitch.threedslibrary.service.Result
import org.json.JSONObject


object AuthenticationSession {

    lateinit var threeDSInstance: TridentSDK
    lateinit var applicationContext: Application
    lateinit var publishableKey: String
    lateinit var transaction: Transaction<`in`.juspay.trident.core.Transaction>

    fun setAuthSessionPublishableKey(publishableKey: String) {
        this.publishableKey = publishableKey
    }

    fun setAuthApplicationContext(applicationContext: Application) {
        this.applicationContext = applicationContext
    }


    fun init(
        clientSecret: String,
        initializationCallback: (Result) -> Unit,
        uiCustomization: UiCustomization? = null,
        tracker: ((JSONObject) -> Unit)? = null,
    ): AuthenticationSession {

        println("init called-------->")

        AuthenticationSession.applicationContext = applicationContext
        ThreeDSFactory.initialize<TridentSDK>(
            ThreeDSSDKType.TRIDENT,
            clientSecret,
            publishableKey
        )

        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()
        threeDSInstance.setClientSecret(clientSecret)


        threeDSInstance.initialise(
            applicationContext, "en-US",
            uiCustomization,
            tracker,
            initializationCallback
        )

        return this

    }

    fun init(
        paymentIntentClientSecret: String?,
        authenticationResponse: String,
        initializationCallback: (Result) -> Unit,
        tracker: ((JSONObject) -> Unit)?,
        uiCustomization: UiCustomization? = null,
    ): AuthenticationSession {
        AuthenticationSession.applicationContext = applicationContext

        ThreeDSFactory.initializeWithAuthResponse<TridentSDK>(
            ThreeDSSDKType.TRIDENT, authenticationResponse,
            publishableKey
        )

        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()
        threeDSInstance.setClientSecret(authenticationResponse)


        threeDSInstance.initialise(
            applicationContext, "en-US",
            uiCustomization,
            tracker,
            initializationCallback
        )

        return this

    }

    fun startAuthentication(activity: Activity, completionCallback: (Result) -> Unit) {
        threeDSInstance.startAuthentication(applicationContext, activity, completionCallback)
    }

    fun createTransaction(
        directoryServerID: String,
        messageVersion: String
    ): Transaction<`in`.juspay.trident.core.Transaction> {

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