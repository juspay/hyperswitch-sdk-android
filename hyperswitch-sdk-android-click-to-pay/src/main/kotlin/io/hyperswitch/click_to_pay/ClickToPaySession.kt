package io.hyperswitch.click_to_pay

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.click_to_pay.models.*

class ClickToPaySession(private val clickToPaySessionLauncher: ClickToPaySessionLauncher){

    constructor(
        activity: Activity,
        publishableKey: String
    ): this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null
    ): this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey,
            customBackendUrl
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null,
        customLogUrl: String? = null,
    ): this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey,
            customBackendUrl,
            customLogUrl,
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null,
        customLogUrl: String? = null,
        customParams: Bundle? = null,
    ): this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey,
            customBackendUrl,
            customLogUrl,
            customParams,
        )
    )

    suspend fun initialise() {
        clickToPaySessionLauncher.initialize()
    }

    suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ) {
        clickToPaySessionLauncher.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        )
    }

    suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse? {
        return clickToPaySessionLauncher.isCustomerPresent(request)
    }

    suspend fun getUserType(): CardsStatusResponse? {
        return clickToPaySessionLauncher.getUserType()
    }

    suspend fun getRecognizedCards(): List<RecognizedCard>? {
        return clickToPaySessionLauncher.getRecognizedCards()
    }

    suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard>? {
        return clickToPaySessionLauncher.validateCustomerAuthentication(otpValue)
    }

    suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse? {
        return clickToPaySessionLauncher.checkoutWithCard(request)
    }

    suspend fun signOut(): SignOutResponse{
        return clickToPaySessionLauncher.signOut()
    }

    suspend fun close() {
        clickToPaySessionLauncher.close()
    }
}
