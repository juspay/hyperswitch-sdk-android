package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsession.SavedPaymentMethodsConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import com.facebook.react.bridge.ReadableMap
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

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(activity, config),
        publishableKey = config?.publishableKey,
        sessionConfig = sessionConfig
    )

    private var paymentSessionHandler: PaymentSessionHandler? = null

    /**
     * Initializes the payment session with the given payment intent client secret.
     *
     * @param sdkAuthorization The client secret of the payment intent.
     */
    fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        this.sessionConfig = sessionConfig
        paymentSessionLauncher.initPaymentSession(sessionConfig)
    }

    @JvmSynthetic
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
        paymentSessionHandler?.updateSdkAuthorization(sdkAuthorization)
    }

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, subscribe, resultCallback)
    }

    @JvmSynthetic
    suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
    ): PaymentSessionHandler {
        return paymentSessionLauncher.getCustomerSavedPaymentMethods(configuration).also {
            paymentSessionHandler = it
        }
    }

    /**
     * Retrieves the customer's saved payment methods.
     *
     * @param configuration Optional configuration to filter saved payment methods.
     * @param savedPaymentMethodCallback A callback that will be invoked with the customer's saved payment methods.
     */
    fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        paymentSessionLauncher.getCustomerSavedPaymentMethods(configuration) {
            paymentSessionHandler = it
            savedPaymentMethodCallback(it)
        }
    }

    fun getPublishableKey(): String {
        return publishableKey ?: ""
    }

    fun getHsConfig(): HyperswitchBaseConfiguration? {
        return (paymentSessionLauncher as? io.hyperswitch.paymentsession.BasePaymentSessionLauncher)?.getHsConfig()
    }

    fun getSdkAuthorization(): String {
        return sessionConfig?.sdkAuthorization ?: ""
    }

    fun getPrefetchedApiData(): Pair<Boolean, ReadableMap?> {
        return (paymentSessionLauncher as? DefaultPaymentSessionLauncher)?.getPrefetchedApiData() ?: Pair(false, null)
    }
}
