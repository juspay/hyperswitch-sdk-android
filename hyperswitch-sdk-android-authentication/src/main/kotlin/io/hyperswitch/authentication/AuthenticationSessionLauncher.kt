package io.hyperswitch.authentication

import io.hyperswitch.click_to_pay.ClickToPaySession

/**
 * Interface defining the contract for authentication session management.
 * 
 * This interface provides methods for initializing authentication sessions,
 * Click to Pay sessions, and 3DS authentication flows.
 */
interface AuthenticationSessionLauncher {

    /**
     * Initializes the underlying SDK and loads required resources.
     * 
     * This method must be called before any other operations to ensure
     * the SDK is properly initialized and ready for use.
     *
     * @throws Exception if SDK initialization fails
     */
    @Throws(Exception::class)
    suspend fun initialize()

    /**
     * Initializes an authentication session with payment credentials.
     * 
     * Stores the authentication credentials for subsequent operations
     * like Click to Pay or 3DS authentication.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initAuthenticationSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String
    )

    /**
     * Initializes a Click to Pay session using stored credentials.
     *
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @return ClickToPaySession instance for managing Click to Pay operations
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initClickToPaySession(
        request3DSAuthentication: Boolean,
    ): ClickToPaySession

    /**
     * Initializes a Click to Pay session with explicit credentials.
     * 
     * This overload allows providing credentials directly without
     * calling initAuthenticationSession first.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @return ClickToPaySession instance for managing Click to Pay operations
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean,
    ): ClickToPaySession

    /**
     * Initializes a 3DS authentication session.
     * 
     * This method sets up the necessary components for handling
     * 3D Secure authentication flows.
     *
     * @throws Exception if 3DS session initialization fails
     */
    @Throws(Exception::class)
    suspend fun initThreeDSSession()
}
