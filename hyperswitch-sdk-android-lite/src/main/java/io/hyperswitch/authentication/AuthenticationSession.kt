package io.hyperswitch.authentication

import android.app.Activity
import android.app.Application
import io.hyperswitch.threedslibrary.authenticationSDKs.TridentSDK
import io.hyperswitch.threedslibrary.core.Transaction
import io.hyperswitch.threedslibrary.data.AuthenticationRequestParameters
import io.hyperswitch.threedslibrary.data.ChallengeParameters
import io.hyperswitch.threedslibrary.di.ThreeDSFactory
import io.hyperswitch.threedslibrary.di.ThreeDSSDKType
import io.hyperswitch.threedslibrary.service.Result


object AuthenticationSession {

    lateinit var threeDSInstance: TridentSDK
    lateinit var applicationContext: Application
    lateinit var publishableKey: String
    lateinit var transaction: Transaction<`in`.juspay.trident.core.Transaction>

    fun setAuthSessionPublishableKey(publishableKey: String) {
        this.publishableKey = publishableKey
    }

    fun setApplicationContext(applicationContext:Application) {
        this.applicationContext = applicationContext
    }

    fun init(
        applicationContext: Application,
        clientSecret: String,
        initializationCallback: (Result) -> Unit
    ): AuthenticationSession {
        AuthenticationSession.applicationContext = applicationContext
        ThreeDSFactory.initialize<TridentSDK>(
            ThreeDSSDKType.TRIDENT,
            clientSecret,
            publishableKey
        )

        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()


        threeDSInstance.initialise(
            applicationContext, "en-US",
            null,
            initializationCallback
        )

        return this

    }

    fun init(
        applicationContext: Application,
        paymentIntentClientSecret: String,
        authenticationResponse: String,
        initializationCallback: (Result) -> Unit
    ): AuthenticationSession {
        AuthenticationSession.applicationContext = applicationContext

        ThreeDSFactory.initializeWithAuthResponse<TridentSDK>(
            ThreeDSSDKType.TRIDENT, authenticationResponse,
            publishableKey
        )

        threeDSInstance = ThreeDSFactory.getService<TridentSDK>()


        threeDSInstance.initialise(
            applicationContext, "en-US",
            null,
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