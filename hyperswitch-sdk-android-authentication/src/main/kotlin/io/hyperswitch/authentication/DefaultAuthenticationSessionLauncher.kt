package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.ClickToPaySession

/**
 * Default implementation of AuthenticationSessionLauncher.
 * 
 * This class manages the lifecycle of authentication and Click to Pay sessions,
 * handling initialization, credential storage, and session creation.
 *
 * @property clickToPaySession The underlying Click to Pay session instance
 * @property clientSecret Stored client secret from payment intent
 * @property profileId Stored merchant profile identifier
 * @property authenticationId Stored authentication session identifier
 * @property merchantId Stored merchant identifier
 */
class DefaultAuthenticationSessionLauncher(
    private val activity: Activity,
    private val publishableKey: String,
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

    /**
     * Initializes the Click to Pay SDK.
     * 
     * Loads required resources and prepares the SDK for use.
     * Must be called before any Click to Pay operations.
     *
     * @throws Exception if SDK initialization fails
     */
    @Throws(Exception::class)
    override suspend fun initialize() {
        clickToPaySession.initialise()
    }

    /**
     * Stores authentication credentials for subsequent operations.
     * 
     * These credentials are used when initializing Click to Pay sessions
     * without explicitly providing them each time.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     */
    @Throws(Exception::class)
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

    /**
     * Initializes a Click to Pay session using stored credentials.
     * 
     * Uses credentials previously set via initAuthenticationSession.
     *
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @return The initialized ClickToPaySession instance
     * @throws Exception if session initialization fails or credentials are not set
     */
    @Throws(Exception::class)
    override suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean
    ): ClickToPaySession {
        return initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
    }

    /**
     * Initializes a Click to Pay session with explicit credentials.
     * 
     * Allows initializing a session without calling initAuthenticationSession first.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @return The initialized ClickToPaySession instance
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    override suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ): ClickToPaySession {
        clickToPaySession.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
        return clickToPaySession
    }

    /**
     * Initializes a 3DS authentication session.
     *
     * @param clientSecret The client secret from the payment intent
     * @param configuration The 3DS configuration settings
     * @param callback Callback with result containing the initialized ThreeDSService or error
     */
    override fun initThreeDSSession(
        clientSecret: String,
        configuration: io.hyperswitch.threeds.models.ThreeDSConfiguration,
        callback: (io.hyperswitch.threeds.api.ThreeDSResult<io.hyperswitch.threeds.api.ThreeDSService>) -> Unit
    ) {
        try {
            val threeDSSession = io.hyperswitch.threeds.api.ThreeDSAuthenticationSession(
                activity = activity,
                apiKey = publishableKey
            )
            
            threeDSSession.initThreeDsSession(
                clientSecret = clientSecret,
                configuration = configuration,
                callback = callback
            )
        } catch (e: Exception) {
            callback(
                io.hyperswitch.threeds.api.ThreeDSResult.Error(
                    message = e.message ?: "Unknown error during 3DS initialization",
                    error = io.hyperswitch.threeds.models.ThreeDSError(
                        code = io.hyperswitch.threeds.models.ThreeDSError.INIT_EXCEPTION,
                        message = e.message ?: "Unknown error",
                        details = e.toString(),
                        errorType = io.hyperswitch.threeds.models.ErrorType.INITIALIZATION_ERROR
                    )
                )
            )
        }
    }
}
