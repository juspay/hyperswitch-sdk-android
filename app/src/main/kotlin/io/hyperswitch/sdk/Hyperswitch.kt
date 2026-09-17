package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchPlatformConfiguration
import io.hyperswitch.react.ReactNativeController

object Hyperswitch {

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
        // Boot the shared host now so the first initPaymentSession does not wait for bundle evaluation.
        ReactNativeController.initialize(activity.application)
        ReactNativeController.warmUp()
        return HyperswitchInstance(activity, config)
    }
}
