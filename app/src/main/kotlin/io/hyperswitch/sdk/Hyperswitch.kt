package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchPlatformConfiguration
import io.hyperswitch.react.ReactNativeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point of the SDK.
 *
 * [init] suspends by contract rather than by need. Today it only boots the shared React
 * host, and nothing a merchant does next has to wait for that. The suspension point is
 * reserved: initialisation that must finish before the instance is usable (validating the
 * publishable key, resolving remote configuration) can be added later without changing a
 * single integration.
 *
 * Whatever is added here must stay quick. Work that only a payment needs belongs behind
 * [HyperswitchInstance.initPaymentSession], which already suspends, so that an app paying
 * for it at launch is never the default.
 */
object Hyperswitch {

    @JvmSynthetic
    suspend fun init(
        activity: Activity,
        config: HyperswitchConfiguration,
    ): HyperswitchInstance = create(activity, config)

    @JvmSynthetic
    suspend fun init(
        activity: Activity,
        config: HyperswitchPlatformConfiguration,
    ): HyperswitchInstance = create(activity, config)

    @JvmSynthetic
    suspend fun init(activity: Activity): HyperswitchInstance = create(activity, config = null)

    // ── Callback forms, for callers without a coroutine scope (Java) ──────────

    fun init(
        activity: Activity,
        config: HyperswitchConfiguration,
        onResult: (HyperswitchInstance) -> Unit,
    ) = initAsync(activity, config, onResult)

    fun init(
        activity: Activity,
        config: HyperswitchPlatformConfiguration,
        onResult: (HyperswitchInstance) -> Unit,
    ) = initAsync(activity, config, onResult)

    fun init(
        activity: Activity,
        onResult: (HyperswitchInstance) -> Unit,
    ) = initAsync(activity, config = null, onResult = onResult)

    private fun initAsync(
        activity: Activity,
        config: HyperswitchBaseConfiguration?,
        onResult: (HyperswitchInstance) -> Unit,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val instance = create(activity, config)
            withContext(Dispatchers.Main) { onResult(instance) }
        }
    }

    private suspend fun create(
        activity: Activity,
        config: HyperswitchBaseConfiguration?,
    ): HyperswitchInstance {
        ReactNativeController.initialize(activity.application)
        // Boots the shared host so the bundle is evaluated before the first session needs
        // it. Returns as soon as the start is scheduled; surfaces started meanwhile wait
        // for the runtime on their own.
        ReactNativeController.warmUp()
        return HyperswitchInstance(activity, config)
    }
}
