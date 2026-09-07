package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A configured SDK instance. Sessions and elements created from it share the one React
 * host; the instance itself holds no runtime state beyond the configuration.
 */
class HyperswitchInstance internal constructor(
    private val activity: Activity,
    private val hsConfig: HyperswitchBaseConfiguration?,
) {

    /**
     * Activity captured at [Hyperswitch.init] — exposed so SDK extension modules
     * (e.g. hyperswitch-sdk-android-payment-methods) can build their own RN hosts/views.
     */
    fun getActivity(): Activity = activity

    /**
     * Base configuration resolved by [Hyperswitch.init] — exposed, as an
     * already-completed deferred, so SDK extension modules (e.g.
     * hyperswitch-sdk-android-payment-methods) can forward the `hyperswitchConfig`
     * payload to their surfaces.
     */
    val configurationDeferred: Deferred<HyperswitchBaseConfiguration?>
        @JvmSynthetic
        get() = CompletableDeferred(hsConfig)

    /** Resolves once the session's prefetch surface is running under these credentials. */
    @JvmSynthetic
    suspend fun initPaymentSession(config: PaymentSessionConfiguration): PaymentSession {
        val session = PaymentSession(activity, hsConfig, config)
        session.initPaymentSession(config)
        session.awaitReady()
        return session
    }

    fun initPaymentSession(
        config: PaymentSessionConfiguration,
        onResult: (PaymentSession) -> Unit,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val session = initPaymentSession(config)
            withContext(Dispatchers.Main) { onResult(session) }
        }
    }

    @JvmSynthetic
    suspend fun elements(config: PaymentSessionConfiguration): Elements =
        Elements(activity, hsConfig, config).also { it.getPaymentSession().awaitReady() }

    fun elements(config: PaymentSessionConfiguration, onResult: (Elements) -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val elements = elements(config)
            withContext(Dispatchers.Main) { onResult(elements) }
        }
    }
}
