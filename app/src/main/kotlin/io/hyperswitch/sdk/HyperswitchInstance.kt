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
    suspend fun initPaymentSession(config: PaymentSessionConfiguration): PaymentSession {
        val hsConfig = if (initDeferred.isCompleted) {
            initDeferred.getCompleted()
        } else {
            initDeferred.await()
        }
        val ps = PaymentSession(activity, hsConfig?.publishableKey, sessionConfig = config, profileId = hsConfig?.profileId)
        ps.initPaymentSession(config.sdkAuthorization)
        return ps
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun initPaymentSession(config: PaymentSessionConfiguration, onResult : (PaymentSession) -> Unit){
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val hsConfig = if (initDeferred.isCompleted) {
                initDeferred.getCompleted()
            } else {
                initDeferred.await()
            }
            val ps = PaymentSession(activity, hsConfig?.publishableKey, sessionConfig = config, profileId = hsConfig?.profileId)
            ps.initPaymentSession(config.sdkAuthorization)
            withContext(Dispatchers.Main) { onResult(ps) }
        }

    }

    suspend fun elements(config: PaymentSessionConfiguration): Elements {
        val hsConfig = initDeferred.await()
        return Elements(activity, hsConfig, config)
    }

    fun elements(config: PaymentSessionConfiguration, onResult: (Elements) -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val hsConfig = initDeferred.await()
            withContext(Dispatchers.Main) { onResult(Elements(activity, hsConfig, config)) }
        }
    }
}