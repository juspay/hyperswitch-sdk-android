package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HyperswitchInstance internal constructor(
    private val activity: Activity,
    private val initDeferred: Deferred<HyperswitchBaseConfiguration?>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    @JvmSynthetic
    suspend fun initPaymentSession(config: PaymentSessionConfiguration): PaymentSession {
        val hsConfig = if (initDeferred.isCompleted) {
            initDeferred.getCompleted()
        } else {
            initDeferred.await()
        }
        val ps = PaymentSession(activity, hsConfig, config)
        ps.initPaymentSession(config)
        return ps
    }

    /** Callback flavor mirrors the suspend one: failures (e.g. SESSION_INIT_IN_PROGRESS when
     *  the same sdkAuthorization is mid-fetch in another session) arrive as Result.failure. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun initPaymentSession(
        config: PaymentSessionConfiguration,
        onResult: (Result<PaymentSession>) -> Unit,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val result = runCatching {
                val hsConfig = if (initDeferred.isCompleted) {
                    initDeferred.getCompleted()
                } else {
                    initDeferred.await()
                }
                val ps = PaymentSession(activity, hsConfig, config)
                ps.initPaymentSession(config)
                ps
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    @JvmSynthetic
    suspend fun elements(config: PaymentSessionConfiguration): Elements {
        val hsConfig = initDeferred.await()
        return Elements.create(activity, hsConfig, config)
    }

    fun elements(
        config: PaymentSessionConfiguration,
        onResult: (Result<Elements>) -> Unit,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val result = runCatching {
                val hsConfig = initDeferred.await()
                /* Create off the main thread — Elements.create runs the prefetch and the
                   React Native bootstrap; only the callback belongs on Main. */
                Elements.create(activity, hsConfig, config)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}