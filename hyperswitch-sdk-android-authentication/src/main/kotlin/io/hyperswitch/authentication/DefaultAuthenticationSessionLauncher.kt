package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.ClickToPaySession
import io.hyperswitch.click_to_pay.models.ClickToPayException

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
    activity: Activity,
    publishableKey: String,
    customBackendUrl: String? = null,
    customLogUrl: String? = null,
    customParams: Bundle? = null,
): AuthenticationSessionLauncher {
    private val clickToPaySession: ClickToPaySession = ClickToPaySession(
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
     * @throws ClickToPayException if session initialization fails or credentials are not set
     */
    @Throws(ClickToPayException::class)
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
     * @throws ClickToPayException if session initialization fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ): ClickToPaySession {
        if (activeClickToPay != null && activeClickToPay !== clickToPaySession) {
            try {
                activeClickToPay?.close()
            }catch(_ : Exception){

            }
        }
        clickToPaySession.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
        activeClickToPay = clickToPaySession
        return clickToPaySession
    }

    /**
     * Get the existing Active ClickToPay Session
     *
     */
    @Throws(Exception::class)
    override suspend fun getActiveClickToPaySession(
        activity: Activity
    ): ClickToPaySession? {
        return activeClickToPay?.getActiveClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            activity
        )
    }

    /**
     * Initializes a 3DS authentication session.
     * 
     * Currently a no-op placeholder for future 3DS functionality.
     */
    @Throws(Exception::class)
    override suspend fun initThreeDSSession() {}
    companion object {
        private  var activeClickToPay: ClickToPaySession? = null
    }
}
