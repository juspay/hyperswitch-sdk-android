package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsession.SavedPaymentMethodsConfiguration
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
    private val publishableKey: String? = null,
    sessionConfig: PaymentSessionConfiguration? = null
) {
    private var sessionConfig = sessionConfig

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncher(activity, config),
        publishableKey = config?.publishableKey,
        sessionConfig = sessionConfig
    )



    internal fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
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

    /**
     * Presents the sheet; events listed in [PaymentSheet.Configuration.subscriptionEvents]
     * are delivered to [onChange]. Pass it by name: `presentPaymentSheet(config, onChange = { ... }) { result -> }`.
     */
    @JvmSynthetic
    suspend fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration,
        onChange: PaymentEventListener,
    ): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            paymentSessionLauncher.presentPaymentSheet(configuration, onChange) { result ->
                continuation.resume(result)
            }
        }
    }

    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration,
        onChange: PaymentEventListener,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configuration, onChange, resultCallback)
    }

    /** Commits the credentials a successful [updateIntent] moved to; the launcher moves the handler. */
    private fun updateSdkAuthorization(sdkAuthorization: String) {
        this.sessionConfig = PaymentSessionConfiguration(sdkAuthorization)
    }

    /** Resolves once the session's runtime is ready to present. */
    @JvmSynthetic
    internal suspend fun awaitReady() {
        (paymentSessionLauncher as? DefaultPaymentSessionLauncher)?.awaitReady()
    }

    /** The session's identity in JS: the root tag of its prefetch surface on the shared host. */
    internal val sessionTag: Int?
        get() = (paymentSessionLauncher as? DefaultPaymentSessionLauncher)?.sessionTag

    /** True while an updateIntent is in flight; a confirm now would pay the intent being replaced. */
    internal val isUpdatingIntent: Boolean
        get() = (paymentSessionLauncher as? DefaultPaymentSessionLauncher)?.isUpdatingIntent ?: false

    /**
     * Stops this session's surfaces on the shared React host. Runs automatically when the
     * hosting activity is destroyed; call it earlier to release a session you are done with.
     */
    fun close() {
        (paymentSessionLauncher as? DefaultPaymentSessionLauncher)?.close()
    }

    /** Replaces the session's intent via the session's prefetch surface. */
    fun updateIntent(
        authorizationProvider: (onAuthorization: (String) -> Unit) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        val launcher = paymentSessionLauncher as? DefaultPaymentSessionLauncher
        if (launcher == null) {
            authorizationProvider { auth ->
                updateSdkAuthorization(auth)
                onResult(Result.success(auth))
            }
            return
        }
        launcher.updateIntent(authorizationProvider) { result ->
            result.onSuccess { updateSdkAuthorization(it) }
            onResult(result)
        }
    }

    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, subscribe, resultCallback)
    }

    /** Map-configured sheet; `configuration.subscriptionEvents` in the map selects the events for [onChange]. */
    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        onChange: PaymentEventListener,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, onChange, resultCallback)
    }

    @JvmSynthetic
    suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
    ): PaymentSessionHandler {
        return paymentSessionLauncher.getCustomerSavedPaymentMethods(configuration)
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
        paymentSessionLauncher.getCustomerSavedPaymentMethods(configuration, savedPaymentMethodCallback)
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
}
