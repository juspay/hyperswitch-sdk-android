package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.model.ElementConfiguration
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration

class Elements internal constructor(
    activity: Activity,
    config: HyperswitchBaseConfiguration?,
    sessionConfiguration: PaymentSessionConfiguration
) {
    private val paymentSession = PaymentSession(activity, config = config, sessionConfig = sessionConfiguration)

    fun bind(config: ElementConfiguration): HyperswitchBoundElement =
        HyperswitchBoundElement(paymentSession, config.element)
}