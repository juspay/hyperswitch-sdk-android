package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.ClickToPaySession

/**
 * Main entry point for authentication and Click to Pay sessions.
 * 
 * This class provides a simplified interface for initializing authentication sessions
 * and Click to Pay functionality. It wraps the DefaultAuthenticationSessionLauncher
 * and provides multiple constructor overloads for different configuration needs.
 *
 * @property authenticationSessionLauncher The underlying launcher implementation
 */
class AuthenticationSession(
    private val authenticationSessionLauncher: DefaultAuthenticationSessionLauncher
) {
    /**
     * Creates an authentication session with basic configuration.
     *
     * @param activity The Android activity context
     * @param publishableKey The publishable API key for authentication
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

    /**
     * Creates an authentication session with custom backend URL.
     *
     * @param activity The Android activity context
     * @param publishableKey The publishable API key for authentication
     * @param customBackendUrl Optional custom backend URL for API calls
     */
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

    /**
     * Creates an authentication session with custom backend and logging URLs.
     *
     * @param activity The Android activity context
     * @param publishableKey The publishable API key for authentication
     * @param customBackendUrl Optional custom backend URL for API calls
     * @param customLogUrl Optional custom URL for logging
     */
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

    /**
     * Creates an authentication session with full configuration options.
     *
     * @param activity The Android activity context
     * @param publishableKey The publishable API key for authentication
     * @param customBackendUrl Optional custom backend URL for API calls
     * @param customLogUrl Optional custom URL for logging
     * @param customParams Optional additional parameters as a Bundle
     */
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

    /**
     * Initializes the authentication session with payment credentials.
     * 
     * This method must be called before initiating Click to Pay sessions.
     * It initializes the SDK and stores the authentication credentials.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @throws Exception if initialization fails
     */
    @Throws(Exception::class)
    suspend fun initAuthenticationSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
    ) {
        authenticationSessionLauncher.initialize()
        
        return authenticationSessionLauncher.initAuthenticationSession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId
        )
    }

    /**
     * Initializes a Click to Pay session with default 3DS authentication enabled.
     *
     * @return ClickToPaySession instance for managing Click to Pay operations
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initClickToPaySession(): ClickToPaySession {
        return initClickToPaySession(true)
    }

    /**
     * Initializes a Click to Pay session with configurable 3DS authentication.
     *
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @return ClickToPaySession instance for managing Click to Pay operations
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean
    ): ClickToPaySession {
        return authenticationSessionLauncher.initClickToPaySession(
            request3DSAuthentication
        )
    }

    /**
     * Initializes a 3DS authentication session.
     * 
     * Creates a ThreeDSAuthenticationSession and initializes it with the provided configuration.
     *
     * @param clientSecret The client secret from the payment intent
     * @param configuration The 3DS configuration settings
     * @param callback Callback with result containing the initialized ThreeDSService or error
     */
    fun initThreeDSSession(
        clientSecret: String,
        configuration: io.hyperswitch.threeds.models.ThreeDSConfiguration,
        callback: (io.hyperswitch.threeds.api.ThreeDSResult<io.hyperswitch.threeds.api.ThreeDSService>) -> Unit
    ) {
        authenticationSessionLauncher.initThreeDSSession(clientSecret, configuration, callback)
    }
}
