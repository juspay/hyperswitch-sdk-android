package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchPlatformConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

object Hyperswitch {
    private var currentScope: CoroutineScope? = null

    fun init(
        activity: Activity,
        config: HyperswitchConfiguration,
    ): HyperswitchInstance = init(activity, config as HyperswitchBaseConfiguration)
    fun init(
        activity: Activity
    ): HyperswitchInstance = init(activity, config = null)


    fun init(
        activity: Activity,
        config: HyperswitchPlatformConfiguration,
    ): HyperswitchInstance = init(activity, config as HyperswitchBaseConfiguration)

    private fun init(
        activity: Activity,
        config: HyperswitchBaseConfiguration?,
    ): HyperswitchInstance {
        currentScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        currentScope = scope
        val initDeferred = scope.async {
            // TODO: async SDK initialisation (e.g. validate publishable key, fetch remote config)
            config
        }
        return HyperswitchInstance(activity, initDeferred = initDeferred)
    }
}