package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.ClickToPaySession

class AuthenticationSession(
    private val authenticationSessionLauncher: DefaultAuthenticationSessionLauncher
) {
    /**
     * Constructor with publishable key (backward compatibility)
     * @param activity The activity context
     * @param publishableKey Publishable key for authentication
     */
    constructor(
        activity: Activity,
        publishableKey: String
    ) : this(
        DefaultAuthenticationSessionLauncher(
            activity = activity,
            publishableKey = publishableKey
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null,
    ) : this(
        DefaultAuthenticationSessionLauncher(
            activity = activity,
            publishableKey = publishableKey,
            customBackendUrl = customBackendUrl
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null,
        customLogUrl: String? = null
    ) : this(
        DefaultAuthenticationSessionLauncher(
            activity = activity,
            publishableKey = publishableKey,
            customBackendUrl = customBackendUrl,
            customLogUrl = customLogUrl,
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null,
        customLogUrl: String? = null,
        customParams: Bundle? = null,
    ) : this(
        DefaultAuthenticationSessionLauncher(
            activity = activity,
            publishableKey = publishableKey,
            customBackendUrl = customBackendUrl,
            customLogUrl = customLogUrl,
            customParams = customParams
        )
    )

    suspend fun initAuthenticationSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
    ) {
        return authenticationSessionLauncher.initAuthenticationSession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId
        )
    }

    suspend fun initClickToPaySession(): ClickToPaySession? {
        return initClickToPaySession(true)
    }

    suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean
    ): ClickToPaySession? {
        return authenticationSessionLauncher.initClickToPaySession(
            request3DSAuthentication
        )
    }

    suspend fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String
    ): ClickToPaySession? {
        return authenticationSessionLauncher.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            true
        )
    }

    suspend fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        request3DSAuthentication: Boolean
    ): ClickToPaySession? {
        return authenticationSessionLauncher.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
    }
}