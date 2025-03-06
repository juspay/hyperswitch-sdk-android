package io.hyperswitch.authentication

import android.app.Activity
import android.app.Application
import `in`.juspay.trident.core.ConfigParameters
import `in`.juspay.trident.core.Transaction
import `in`.juspay.trident.data.AuthenticationRequestParameters
import `in`.juspay.trident.data.ChallengeParameters
import `in`.juspay.trident.data.ChallengeStatusReceiver
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
        this.applicationContext = applicationContext
        ThreeDSFactory.initialize<TridentSDK>(
            ThreeDSSDKType.TRIDENT,
            clientSecret,
            "pk_snd_23ff7c6d50e5424ba2e88415772380cd"
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