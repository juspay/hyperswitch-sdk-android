package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.sdk.PaymentSession
import io.hyperswitch.model.PaymentSessionConfiguration
import kotlinx.coroutines.Deferred

class HyperswitchInstance internal constructor(
    private val activity: Activity,
    private val initDeferred: Deferred<HyperswitchBaseConfiguration?>,
) {
    suspend fun initPaymentSession(config: PaymentSessionConfiguration): PaymentSession? {
        val hsConfig = initDeferred.await()
        // TODO: suspend for async session validation / token exchange with Hyperswitch backend
        hsConfig?.publishableKey?.let {
            val ps =  PaymentSession(activity, hsConfig.publishableKey, sessionConfig = config)
            ps.initPaymentSession(config.sdkAuthorization)
            return ps
        }
        throw Exception("Failed to initialise Hyperswitch")
    }

    suspend fun elements(config: PaymentSessionConfiguration): Elements {
        val hsConfig = initDeferred.await()
        // TODO: async setup (e.g. prefetch element config from Hyperswitch backend)
        hsConfig?.let {
            return Elements(activity, hsConfig, config)
        }
        throw Exception("Failed to initialise Hyperswitch")
    }
}