package io.hyperswitch.click_to_pay

import android.app.Activity
import io.hyperswitch.click_to_pay.models.*

class ClickToPaySession(private val clickToPaySessionLauncher: ClickToPaySessionLauncher) {

    constructor(
        activity: Activity,
        publishableKey: String
    ) : this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey
        )
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String? = null
    ) : this(
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
    ) : this(
        DefaultClickToPaySessionLauncher(
            activity,
            publishableKey,
            customBackendUrl,
            customLogUrl,
        )
    )

    @Throws(ClickToPayException::class)
    @JvmSynthetic
    suspend fun initialise() {
        clickToPaySessionLauncher.initialize()
    }

    @JvmSynthetic
    suspend fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
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

    @JvmSynthetic
    suspend fun getActiveClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        activity: Activity
    ): ClickToPaySession {
        clickToPaySessionLauncher.getActiveClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            activity
        )
        return this
    }

    @JvmSynthetic
    suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse {
        return clickToPaySessionLauncher.isCustomerPresent(request)
    }

    @JvmSynthetic
    suspend fun getUserType(): CardsStatusResponse {
        return clickToPaySessionLauncher.getUserType()
    }

    @JvmSynthetic
    suspend fun getRecognizedCards(): List<RecognizedCard> {
        return clickToPaySessionLauncher.getRecognizedCards()
    }

    @JvmSynthetic
    suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard> {
        return clickToPaySessionLauncher.validateCustomerAuthentication(otpValue)
    }

    @JvmSynthetic
    suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        return clickToPaySessionLauncher.checkoutWithCard(request)
    }

    @JvmSynthetic
    suspend fun signOut(): SignOutResponse {
        return clickToPaySessionLauncher.signOut()
    }

    @JvmSynthetic
    suspend fun close() {
        clickToPaySessionLauncher.close(true)
    }

    @JvmSynthetic
    @Deprecated("this method will be removed")
    suspend fun closeInternal() {
        clickToPaySessionLauncher.close(false)
    }
}
