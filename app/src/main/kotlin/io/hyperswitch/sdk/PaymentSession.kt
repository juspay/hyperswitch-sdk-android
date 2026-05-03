package io.hyperswitch.sdk

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A class that manages payment sessions using a [io.hyperswitch.paymentsession.PaymentSessionLauncher].
 *
 * This class provides methods for initializing a payment session, presenting a payment sheet,
 * and retrieving customer saved payment methods.
 */
class PaymentSession internal constructor(
    private val paymentSessionLauncher: PaymentSessionLauncher,
    private val config: HyperswitchBaseConfiguration?,
    sessionConfig: PaymentSessionConfiguration? = null
) {
    private val publishableKey = config?.publishableKey
    private var sessionConfig = sessionConfig

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration): this(
        DefaultPaymentSessionLauncher(activity, config, null),
        config,
        sessionConfig
    )

    constructor(activity: Activity, publishableKey: String?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(
            activity,
            HyperswitchConfiguration(publishableKey = publishableKey),
            null
        ),
        HyperswitchConfiguration(publishableKey = publishableKey),
        sessionConfig
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(
            activity,
            HyperswitchConfiguration(
                publishableKey = publishableKey,
                customConfig = CustomEndpointConfiguration(
                    overrideCustomBackendEndpoint = customBackendUrl,
                    overrideCustomLoggingEndpoint = customLogUrl
                )
            ),
            null
        ),
        HyperswitchConfiguration(
            publishableKey = publishableKey,
            customConfig = CustomEndpointConfiguration(
                overrideCustomBackendEndpoint = customBackendUrl,
                overrideCustomLoggingEndpoint = customLogUrl
            )
        ),
        null
    )

    constructor(activity: Activity, publishableKey: String, customParams: Bundle) : this(
        DefaultPaymentSessionLauncher(
            activity,
            HyperswitchConfiguration(publishableKey = publishableKey),
            customParams
        ),
        HyperswitchConfiguration(publishableKey = publishableKey),
        null
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String,
        customParams: Bundle
    ) : this(
        DefaultPaymentSessionLauncher(
            activity,
            HyperswitchConfiguration(
                publishableKey = publishableKey,
                customConfig = CustomEndpointConfiguration(
                    overrideCustomBackendEndpoint = customBackendUrl,
                    overrideCustomLoggingEndpoint = customLogUrl
                )
            ),
            customParams
        ),
        HyperswitchConfiguration(
            publishableKey = publishableKey,
            customConfig = CustomEndpointConfiguration(
                overrideCustomBackendEndpoint = customBackendUrl,
                overrideCustomLoggingEndpoint = customLogUrl
            )
        ),
        null
    )

    /**
     * A builder class for creating instances of [PaymentSession].
     *
     * @param activity The activity that will host the payment sheet.
     * @param publishableKey The publishable key for your Hyperswitch account.
     */
    class Builder(private val activity: Activity, private val publishableKey: String) {
        private var customBackendUrl: String? = null
        private var customLogEndpoint: String? = null
        private var customParams: Bundle? = null
        private var customEndpoint: String? = null
        private var customAssetsEndpoint: String? = null
        private var customSDKConfigEndpoint: String? = null
        private var customConfirmEndpoint: String? = null
        private var customAirborneEndpoint: String? = null

        fun customBackendUrl(url: String) = apply { this.customBackendUrl = url }
        fun customLogEndpoint(url: String) = apply { this.customLogEndpoint = url }
        fun customParams(params: Bundle) = apply { this.customParams = params }
        fun customEndpoint(url: String) = apply { this.customEndpoint = url }
        fun customAssetsEndpoint(url: String) = apply { this.customAssetsEndpoint = url }
        fun customSDKConfigEndpoint(url: String) = apply { this.customSDKConfigEndpoint = url }
        fun customConfirmEndpoint(url: String) = apply { this.customConfirmEndpoint = url }
        fun customAirborneEndpoint(url: String) = apply { this.customAirborneEndpoint = url }

        fun build(): PaymentSession {
            val config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                customConfig = CustomEndpointConfiguration(
                    customEndpoint = customEndpoint,
                    overrideCustomBackendEndpoint = customBackendUrl,
                    overrideCustomAssetsEndpoint = customAssetsEndpoint,
                    overrideCustomSDKConfigEndpoint = customSDKConfigEndpoint,
                    overrideCustomConfirmEndpoint = customConfirmEndpoint,
                    overrideCustomAirborneEndpoint = customAirborneEndpoint,
                    overrideCustomLoggingEndpoint = customLogEndpoint
                )
            )
            val launcher = DefaultPaymentSessionLauncher(activity, config, customParams)
            return PaymentSession(launcher, config, null)
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


    fun updateSdkAuthorization(sdkAuthorization: String) {
        this.sessionConfig = PaymentSessionConfiguration(sdkAuthorization)
        paymentSessionLauncher.initPaymentSession(sdkAuthorization)
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

    internal fun getConfiguration(): HyperswitchBaseConfiguration? {
        return config
    }
}
