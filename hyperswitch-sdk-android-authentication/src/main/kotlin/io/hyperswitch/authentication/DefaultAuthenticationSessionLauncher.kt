package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.ClickToPaySession

/**
 * Default implementation of AuthenticationSessionLauncher for the lite SDK
 * Handles authentication session lifecycle and configuration
 */
class DefaultAuthenticationSessionLauncher(
    activity: Activity,
    publishableKey: String,
    customBackendUrl: String? = null,
    customLogUrl: String? = null,
    customParams: Bundle? = null,
): AuthenticationSessionLauncher {
    private var clickToPaySession: ClickToPaySession = ClickToPaySession(
        activity,
        publishableKey,
        customBackendUrl,
        customLogUrl,
        customParams
    )
    private var clientSecret: String? = null
    private var profileId: String? = null
    private var authenticationId: String? = null
    private var merchantId: String? = null

    override suspend fun initAuthenticationSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
    ) {
        this.clientSecret = clientSecret
        this.profileId = profileId
        this.authenticationId = authenticationId
        this.merchantId = merchantId
    }

    override suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean
    ): ClickToPaySession? {
        return initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
    }

    override suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ): ClickToPaySession? {
            clickToPaySession.initClickToPaySession(
                clientSecret,
                profileId,
                authenticationId,
                merchantId,
                request3DSAuthentication
            )
            return clickToPaySession
    }

    override suspend fun initThreeDSSession() {}
}