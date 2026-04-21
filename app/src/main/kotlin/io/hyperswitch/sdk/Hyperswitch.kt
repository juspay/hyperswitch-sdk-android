package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchPlatformConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

object Hyperswitch {
    fun init(
        activity: Activity,
        config: HyperswitchConfiguration,
    ): HyperswitchInstance = init(activity, config as HyperswitchBaseConfiguration)

    fun init(
        activity: Activity,
        config: HyperswitchPlatformConfiguration,
    ): HyperswitchInstance = init(activity, config as HyperswitchBaseConfiguration)

    private fun init(
        activity: Activity,
        config: HyperswitchBaseConfiguration,
    ): HyperswitchInstance {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val initDeferred = scope.async {
            // TODO: async SDK initialisation (e.g. validate publishable key, fetch remote config)
            config
        }
        return HyperswitchInstance(activity, initDeferred = initDeferred)
    }
}