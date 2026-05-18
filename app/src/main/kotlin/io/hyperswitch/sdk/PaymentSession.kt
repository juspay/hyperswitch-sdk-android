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
    constructor(activity: Activity, publishableKey: String) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null, null),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity, publishableKey: String, customBackendUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, customBackendUrl, null, null, null),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration): this(
        DefaultPaymentSessionLauncher(activity,
            config?.publishableKey,
            config?.customConfig?.overrideCustomBackendEndpoint,
            config?.customConfig?.overrideCustomLoggingEndpoint,
            config?.customConfig?.commonEndpoint,
            null,),
        publishableKey = config?.publishableKey,
        sessionConfig = sessionConfig
    )

    constructor(activity: Activity, publishableKey: String?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null, null),
        publishableKey = publishableKey,
        sessionConfig = sessionConfig
    )


    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        commonEndpoint: String,
        customLogUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, commonEndpoint,null
        ),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, publishableKey: String, customParams: Bundle) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null, customParams),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String,
        commonEndpoint: String,
        customParams: Bundle
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, commonEndpoint, customParams
        ),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    private var paymentSessionHandler : PaymentSessionHandler? = null
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
        paymentSessionHandler?.updateSdkAuthorization(sdkAuthorization)
    }

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, subscribe, resultCallback)
    }

    suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler {
        return paymentSessionLauncher.getCustomerSavedPaymentMethods().also {
            paymentSessionHandler = it
        }
    }

    /**
     * Retrieves the customer's saved payment methods.
     *
     * @param savedPaymentMethodCallback A callback that will be invoked with the customer's saved payment methods.
     */
    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)) {
        paymentSessionLauncher.getCustomerSavedPaymentMethods {
            paymentSessionHandler = it
            savedPaymentMethodCallback(it)
        }
    }

    fun getPublishableKey(): String {
        return publishableKey ?: ""
    }

    fun getSdkAuthorization(): String {
        return sessionConfig?.sdkAuthorization ?: ""
    }

    /*** A builder class for creating instances of [PaymentSession].
     *
     * @param activity The activity that will host the payment sheet.
     * @param publishableKey The publishable key for your Stripe account.
     */
    class Builder(private val activity: Activity, private val publishableKey: String) {
        private var customBackendUrl: String? = null
        private var customLogUrl: String? = null
        private var customParams: Bundle? = null
        private var commonEndpoint: String?= null

        fun customBackendUrl(url: String) = apply { this.customBackendUrl = url }
        fun customLogUrl(url: String) = apply { this.customLogUrl = url }
        fun customParams(params: Bundle) = apply { this.customParams = params }

        fun build(): PaymentSession {
            val launcher = DefaultPaymentSessionLauncher(
                activity, publishableKey, customBackendUrl, customLogUrl, commonEndpoint, customParams
            )
            return PaymentSession(launcher, publishableKey, null)
        }
    }
}
