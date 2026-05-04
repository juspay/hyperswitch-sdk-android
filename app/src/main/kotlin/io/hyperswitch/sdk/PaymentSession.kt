package io.hyperswitch.sdk

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import io.hyperswitch.react.HyperEventEmitter

/**
 * A class that manages payment sessions using a [io.hyperswitch.paymentsession.PaymentSessionLauncher].
 *
 * This class provides methods for initializing a payment session, presenting a payment sheet,
 * and retrieving customer saved payment methods.
 */
class PaymentSession internal constructor(
    private val paymentSessionLauncher: PaymentSessionLauncher,
    private val publishableKey: String? = null,
    sessionConfig: PaymentSessionConfiguration? = null
) {
    private var sessionConfig = sessionConfig
    constructor(activity: Activity, publishableKey: String, profileId: String? = null) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null, profileId),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity, publishableKey: String, customBackendUrl: String, profileId: String? = null
    ) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, customBackendUrl, null, null, profileId),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration): this(
        DefaultPaymentSessionLauncher(activity,
            config?.publishableKey,
            config?.customConfig?.overrideCustomBackendEndpoint,
            config?.customConfig?.overrideCustomLoggingEndpoint,
            null,
            config?.profileId),
        publishableKey = config?.publishableKey,
        sessionConfig = sessionConfig
    )

    constructor(activity: Activity, publishableKey: String?, sessionConfig: PaymentSessionConfiguration, profileId: String? = null) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null, profileId),
        publishableKey = publishableKey,
        sessionConfig = sessionConfig
    )


    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String,
        profileId: String? = null,
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, null, profileId
        ),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, publishableKey: String, customParams: Bundle, profileId: String? = null) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, customParams, profileId),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String,
        customParams: Bundle,
        profileId: String? = null,
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, customParams, profileId
        ),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    /*** A builder class for creating instances of [PaymentSession].
     *
     * @param activity The activity that will host the payment sheet.
     * @param publishableKey The publishable key for your Stripe account.
     */
    class Builder(private val activity: Activity, private val publishableKey: String) {
        private var customBackendUrl: String? = null
        private var customLogUrl: String? = null
        private var customParams: Bundle? = null
        private var profileId: String? = null

        fun customBackendUrl(url: String) = apply { this.customBackendUrl = url }
        fun customLogUrl(url: String) = apply { this.customLogUrl = url }
        fun customParams(params: Bundle) = apply { this.customParams = params }
        fun profileId(profileId: String?) = apply { this.profileId = profileId }

        fun build(): PaymentSession {
            val launcher = DefaultPaymentSessionLauncher(
                activity, publishableKey, customBackendUrl, customLogUrl, customParams, profileId
            )
            return PaymentSession(launcher, publishableKey, null)
        }
    }

    /**
     * Initializes the payment session with the given payment intent client secret.
     *
     * @param sdkAuthorization The client secret of the payment intent.
     */
    fun initPaymentSession(sdkAuthorization: String) {
        paymentSessionLauncher.initPaymentSession(sdkAuthorization)
    }

    suspend fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
    ): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            paymentSessionLauncher.presentPaymentSheet(configuration, subscribe) { result ->
                continuation.resume(result)
            }
        }
    }

    fun presentPaymentSheet(subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null, resultCallback: (PaymentResult) -> Unit) {
        paymentSessionLauncher.presentPaymentSheet(configuration = null, subscribe, resultCallback)
    }

    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configuration, subscribe, resultCallback)
    }


    fun updateSdkAuthorization(sdkAuthorization: String){
        this.sessionConfig = PaymentSessionConfiguration(sdkAuthorization)
    }

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, subscribe, resultCallback)
    }

    suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler {
        return paymentSessionLauncher.getCustomerSavedPaymentMethods()
    }

    /**
     * Retrieves the customer's saved payment methods.
     *
     * @param savedPaymentMethodCallback A callback that will be invoked with the customer's saved payment methods.
     */
    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)) {
        paymentSessionLauncher.getCustomerSavedPaymentMethods(savedPaymentMethodCallback)
    }

    fun getPublishableKey(): String {
        return publishableKey ?: ""
    }

    fun getSdkAuthorization(): String {
        return sessionConfig?.sdkAuthorization ?: ""
    }
}
