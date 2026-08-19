package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.SavedPaymentMethodsConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import com.facebook.react.bridge.ReadableMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A class that manages a full-SDK payment session.
 *
 * This class provides methods for initializing a payment session, presenting a payment sheet,
 * and retrieving customer saved payment methods.
 */
class PaymentSession internal constructor(
    private val paymentSessionLauncher: DefaultPaymentSessionLauncher,
    private val publishableKey: String? = null,
    sessionConfig: PaymentSessionConfiguration
) {
    private var sessionConfig = sessionConfig

    internal constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(activity, config),
        publishableKey = config?.publishableKey,
        sessionConfig = sessionConfig
    )

    /**
     * Initializes the payment session and prefetches the data the payment flows need.
     *
     * Suspends until the prefetch settles, so the merchant can treat a returned session as ready
     * to present. A prefetch failure is not fatal — it is reported and the flows fall back to
     * fetching for themselves.
     *
     * @param sessionConfig The session configuration including the SDK authorization.
     */
    internal suspend fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
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

    /** Fetches the new intent's data without mutating the active session. */
    internal suspend fun prepareIntentUpdate(sdkAuthorization: String): Result<ReadableMap> =
        paymentSessionLauncher.prepareIntentUpdate(PaymentSessionConfiguration(sdkAuthorization))

    internal fun commitIntentUpdate(
        sdkAuthorization: String,
        prefetchedApiData: ReadableMap,
    ) {
        val previousAuthorization = sessionConfig.sdkAuthorization
        val newConfig = PaymentSessionConfiguration(sdkAuthorization)
        sessionConfig = newConfig
        paymentSessionLauncher.commitIntentUpdate(newConfig, prefetchedApiData)
        if (previousAuthorization != sdkAuthorization) {
            paymentSessionLauncher.clearPrefetch(previousAuthorization)
        }
    }

    internal fun clearUnappliedPrefetch(sdkAuthorization: String) {
        if (sdkAuthorization != sessionConfig.sdkAuthorization) {
            paymentSessionLauncher.clearPrefetch(sdkAuthorization)
        }
    }

    internal fun handlePaymentResult(result: PaymentResult) {
        paymentSessionLauncher.clearAfterTerminalResult(sessionConfig.sdkAuthorization, result)
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
    ): PaymentSessionHandler =
        paymentSessionLauncher.getCustomerSavedPaymentMethods(configuration)

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
        paymentSessionLauncher.getCustomerSavedPaymentMethods(
            configuration,
            savedPaymentMethodCallback,
        )
    }

    fun getPublishableKey(): String {
        return publishableKey ?: ""
    }

    fun getHsConfig(): HyperswitchBaseConfiguration? {
        return paymentSessionLauncher.getHsConfig()
    }

    fun getSdkAuthorization(): String {
        return sessionConfig.sdkAuthorization
    }

    fun getPrefetchedApiData(): ReadableMap? = paymentSessionLauncher.getPrefetchedApiData()
}
