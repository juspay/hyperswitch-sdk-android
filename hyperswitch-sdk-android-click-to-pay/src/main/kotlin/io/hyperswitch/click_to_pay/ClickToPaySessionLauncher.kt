package io.hyperswitch.click_to_pay

import io.hyperswitch.click_to_pay.models.*

/**
 * Interface for Click to Pay session launcher
 * Defines the contract for managing Click to Pay sessions and operations
 */
interface ClickToPaySessionLauncher {

    suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    )

    /**
     * Check if a customer has an existing Click to Pay profile
     * @param request Customer identification details (email or mobile number)
     * @return CustomerPresenceResponse indicating if customer exists
     */
    suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse?
    
    /**
     * Retrieve the status of customer's saved cards
     * @return CardsStatusResponse with status code
     */
    suspend fun getUserType(): CardsStatusResponse?
    
    /**
     * Get the list of recognized cards for the customer
     * @return List of RecognizedCard objects
     */
    suspend fun getRecognizedCards(): List<RecognizedCard>?
    
    /**
     * Validate customer authentication with OTP
     * @param otpValue The OTP value entered by the customer
     * @return List of RecognizedCard objects if validation successful
     */
    suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard>?
    
    /**
     * Checkout with a selected card
     * @param request CheckoutRequest containing card details and preferences
     * @return CheckoutResponse with transaction details
     */
    suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse?
}
