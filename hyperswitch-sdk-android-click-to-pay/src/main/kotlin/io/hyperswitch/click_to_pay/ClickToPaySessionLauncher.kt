package io.hyperswitch.click_to_pay

import android.app.Activity
import io.hyperswitch.click_to_pay.models.*

/**
 * Interface defining the contract for Click to Pay session management.
 *
 * This interface provides methods for managing Click to Pay sessions,
 * customer verification, card retrieval, and payment processing.
 */
interface ClickToPaySessionLauncher {

    val publishableKey: String

    /**
     * Initializes the Click to Pay SDK.
     *
     * Loads required resources and prepares the SDK for use.
     * Must be called before any other Click to Pay operations.
     *
     * @throws Exception if SDK initialization fails
     */
    @Throws(ClickToPayException::class)
    suspend fun initialize()

    /**
     * Initializes a Click to Pay session with payment credentials.
     *
     * Sets up the session with merchant and payment information required
     * for Click to Pay operations.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @throws Exception if session initialization fails
     */
    @Throws(ClickToPayException::class)
    suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    )

    @Throws(ClickToPayException::class)
    suspend fun getActiveClickToPaySession(
        activity: Activity
    )


    /**
     * Checks if a customer has an existing Click to Pay profile.
     *
     * Verifies whether the customer is enrolled in Click to Pay
     * based on their email or mobile number.
     *
     * @param request Customer identification details (email or mobile number)
     * @return CustomerPresenceResponse indicating if customer exists
     * @throws Exception if the check fails
     */
    @Throws(ClickToPayException::class)
    suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse

    /**
     * Retrieves the status of customer's saved cards.
     *
     * Determines whether the customer has recognized cards available
     * or if additional authentication is required.
     *
     * @return CardsStatusResponse with status code indicating card availability
     * @throws ClickToPayException if retrieval fails with error details
     */
    @Throws(ClickToPayException::class)
    suspend fun getUserType(): CardsStatusResponse

    /**
     * Gets the list of recognized cards for the customer.
     *
     * Retrieves all cards associated with the customer's Click to Pay profile
     * that can be used for payment.
     *
     * @return List of RecognizedCard objects with card details
     * @throws Exception if card retrieval fails
     */
    @Throws(ClickToPayException::class)
    suspend fun getRecognizedCards(): List<RecognizedCard>

    /**
     * Validates customer authentication with OTP.
     *
     * Verifies the OTP entered by the customer and returns their
     * recognized cards upon successful validation.
     *
     * @param otpValue The OTP value entered by the customer
     * @return List of RecognizedCard objects if validation successful
     * @throws ClickToPayException if OTP validation fails with error details
     */
    @Throws(ClickToPayException::class)
    suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard>

    /**
     * Processes checkout with a selected card.
     *
     * Initiates payment processing using the customer's selected
     * Click to Pay card.
     *
     * @param request CheckoutRequest containing card ID and preferences
     * @return CheckoutResponse with transaction details and status
     * @throws ClickToPayException if checkout fails
     */
    @Throws(ClickToPayException::class)
    suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse

    /**
     * Processes signOut to clear the cookies
     *
     * @return SignOutResponse with transaction details and status
     * @throws ClickToPayException if checkout fails
     */
    @Throws(ClickToPayException::class)
    suspend fun signOut(): SignOutResponse

    /**
     * Closes and destroys the Click to Pay session.
     *
     * Performs cleanup by:
     * - Cancelling all pending requests
     * - Restoring accessibility settings
     * - Removing and destroying the WebView
     * - Clearing all cached data and resources
     *
     * After calling this method, the session cannot be used again.
     * A new instance must be created for subsequent operations.
     *
     * @throws ClickToPayException if cleanup fails
     */
    @Throws(ClickToPayException::class)
    suspend fun close()
}
