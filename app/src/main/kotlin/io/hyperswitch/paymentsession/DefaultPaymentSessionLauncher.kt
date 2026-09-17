package io.hyperswitch.paymentsession

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class DefaultPaymentSessionLauncher(
    activity: Activity,
    hsConfig: HyperswitchBaseConfiguration?,
    private var paymentSessionReactLauncher: SDKInterface = PaymentSessionReactLauncher(activity, hsConfig)
) : BasePaymentSessionLauncher(activity, hsConfig) {

    private var activityWatcher: Application.ActivityLifecycleCallbacks? = null

    init {
        val publishableKey = hsConfig?.publishableKey
        if (publishableKey != null) {
            val loggingEndPoint =
                hsConfig.customConfig?.overrideEndpoints?.customLoggingEndpoint
                    ?.takeIf { it.isNotEmpty() }
                    ?: getLoggingUrl(publishableKey)
            HyperLogManager.initialise(publishableKey, loggingEndPoint)
            HyperLogManager.sendLogsFromFile(LogFileManager(activity))
        }
        paymentSessionReactLauncher.initializeReactNativeInstance()
        closeWithActivity()
    }

    /** The session's identity in JS: the root tag of its prefetch surface. */
    internal val sessionTag: Int?
        get() = paymentSessionReactLauncher.sessionTag

    internal val isUpdatingIntent: Boolean
        get() = (paymentSessionReactLauncher as? PaymentSessionReactLauncher)?.isUpdatingIntent ?: false

    override fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        super.initPaymentSession(sessionConfig)
        paymentSessionReactLauncher.sessionConfig = sessionConfig
        paymentSessionReactLauncher.prefetch()
    }

    suspend fun awaitReady() = paymentSessionReactLauncher.awaitReady()

    /** Session-level updateIntent; commits the base [sessionConfig] on success. */
    fun updateIntent(
        authorizationProvider: (onAuthorization: (String) -> Unit) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        paymentSessionReactLauncher.updateIntent(authorizationProvider) { result ->
            result.onSuccess { sessionConfig = PaymentSessionConfiguration(it) }
            onResult(result)
        }
    }

    /** Stops the session's surfaces. Also runs when the hosting Activity is destroyed. */
    fun close() {
        activityWatcher?.let { activity.application?.unregisterActivityLifecycleCallbacks(it) }
        activityWatcher = null
        paymentSessionReactLauncher.close()
    }

    /**
     * Any Activity, not only lifecycle-aware ones (Flutter's FlutterActivity is a plain
     * Activity): the Application reports the destruction of the one this session was
     * created for, and the session closes with it.
     */
    private fun closeWithActivity() {
        val application = activity.application ?: return
        if (activity.isDestroyed) {
            close()
            return
        }
        val watcher = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === this@DefaultPaymentSessionLauncher.activity) close()
            }
        }
        activityWatcher = watcher
        application.registerActivityLifecycleCallbacks(watcher)
    }

    private fun buildSubscription(
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?
    ): Pair<List<String>, PaymentEventListener?> {
        subscribe ?: return emptyList<String>() to null
        val builder = PaymentEventSubscriptionBuilder()
        builder.subscribe()
        val (subscription, listener) = builder.build()
        return subscription.getSubscribedEventStrings() to listener
    }

    override fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration?,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        val (events, listener) = buildSubscription(subscribe)
        paymentSessionReactLauncher.presentSheet(sessionConfig, configuration, events, listener, resultCallback)
    }

    override fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)?,
        resultCallback: (PaymentResult) -> Unit
    ) {
        val (events, listener) = buildSubscription(subscribe)
        paymentSessionReactLauncher.presentSheet(configurationMap, events, listener, resultCallback)
    }

    override fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        paymentSessionReactLauncher.startSavedPaymentMethods(configuration, savedPaymentMethodCallback)
    }

    override fun getCustomerSavedPaymentMethods(
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        getCustomerSavedPaymentMethods(null, savedPaymentMethodCallback)
    }

    override suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
    ): PaymentSessionHandler =
        suspendCancellableCoroutine { continuation ->
            paymentSessionReactLauncher.startSavedPaymentMethods(configuration) { handler ->
                if (continuation.isActive) continuation.resume(handler)
            }
        }
}
