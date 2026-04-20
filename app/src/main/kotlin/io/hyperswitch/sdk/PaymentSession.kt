package io.hyperswitch.sdk

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
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
    private val publishableKey: String? = null,
    private val sessionConfig: PaymentSessionConfiguration? = null
) {
    constructor(activity: Activity, publishableKey: String) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity, publishableKey: String, customBackendUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, customBackendUrl, null, null),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, config: HyperswitchBaseConfiguration, sessionConfig: PaymentSessionConfiguration): this(
        DefaultPaymentSessionLauncher(activity,
            config.publishableKey,
            config.customConfig?.overrideCustomBackendEndpoint,
            config.customConfig?.overrideCustomLoggingEndpoint,
            null),
        publishableKey = config.publishableKey,
        sessionConfig = sessionConfig
    )

    constructor(activity: Activity, publishableKey: String?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, null),
        publishableKey = publishableKey,
        sessionConfig = sessionConfig
    )


    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, null
        ),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(activity: Activity, publishableKey: String, customParams: Bundle) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, null, null, customParams),
        publishableKey = publishableKey,
        sessionConfig = null
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        customBackendUrl: String,
        customLogUrl: String,
        customParams: Bundle
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, customBackendUrl, customLogUrl, customParams
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

        fun customBackendUrl(url: String) = apply { this.customBackendUrl = url }
        fun customLogUrl(url: String) = apply { this.customLogUrl = url }
        fun customParams(params: Bundle) = apply { this.customParams = params }

        fun build(): PaymentSession {
            val launcher = DefaultPaymentSessionLauncher(
                activity, publishableKey, customBackendUrl, customLogUrl, customParams
            )
            return PaymentSession(launcher, publishableKey, null)
        }
    }

    /**
     * Initializes the payment session with the given payment intent client secret.
     *
     * @param paymentIntentClientSecret The client secret of the payment intent.
     */
    fun initPaymentSession(sessionToken: String) {
        paymentSessionLauncher.initPaymentSession(sessionToken)
    }

    suspend fun presentPaymentSheet(configuration: PaymentSheet.Configuration): PaymentSheetResult {
        return suspendCancellableCoroutine { continuation ->
            paymentSessionLauncher.presentPaymentSheet(configuration) { result ->
                continuation.resume(result)
            }
        }
    }
    /**
     * Presents the payment sheet to the user.
     *
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(resultCallback: (PaymentSheetResult) -> Unit) {
        paymentSessionLauncher.presentPaymentSheet(configuration = null, resultCallback)
    }

    /**
     * Presents the payment sheet to the user.
     *
     * @param configuration The configuration for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration, resultCallback: (PaymentSheetResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configuration, resultCallback)
    }

    /**
     * Presents the payment sheet to the user with a configuration map.
     *
     * @param configurationMap The configuration map for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, resultCallback)
    }

    /**
     * Retrieves the customer's saved payment methods.
     *
     * @param savedPaymentMethodCallback A callback that will be invoked with the customer's saved payment methods.
     */
    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)) {
        paymentSessionLauncher.getCustomerSavedPaymentMethods(savedPaymentMethodCallback)
    }

    /**
     * Returns the publishable key for this payment session.
     */
    fun getPublishableKey(): String {
        return publishableKey ?: ""
    }

    /**
     * Returns the SDK authorization for this payment session.
     */
    fun getSdkAuthorization(): String {
        return sessionConfig?.sdkAuthorization ?: ""
    }
}