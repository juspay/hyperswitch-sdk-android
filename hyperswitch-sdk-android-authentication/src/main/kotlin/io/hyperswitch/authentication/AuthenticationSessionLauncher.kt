package io.hyperswitch.authentication

import io.hyperswitch.click_to_pay.ClickToPaySession

interface AuthenticationSessionLauncher {

    suspend fun initAuthenticationSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String
    )

    suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean,
    ): ClickToPaySession?

    suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean,
    ): ClickToPaySession?

    suspend fun initThreeDSSession()
}
