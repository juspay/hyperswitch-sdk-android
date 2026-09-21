package io.hyperswitch.paymentmethods

import android.app.Activity
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.paymentmethods.react.PaymentMethodsRuntime
import io.hyperswitch.sdk.HyperswitchInstance

/** [sdkAuthorization] comes from your server's payment method session. */
data class PaymentMethodSessionConfiguration(val sdkAuthorization: String)

/** A payment method session: the place card forms come from. */
class PaymentMethodSession internal constructor(
    private val activity: Activity,
    private val hyperswitchConfiguration: HyperswitchBaseConfiguration,
    private val configuration: PaymentMethodSessionConfiguration,
) {

    init {
        /* Starts the Payment Methods engine loading, so the first form does not wait for it. */
        PaymentMethodsRuntime.get(activity.application).apply {
            follow(activity)
            warmUp()
        }
    }

    fun createCardForm(configuration: CardForm.Configuration = CardForm.Configuration()): CardForm =
        CardForm(activity, hyperswitchConfiguration, this.configuration, configuration)
}

/** An instance made without a configuration has no key; the form then says so itself. */
fun HyperswitchInstance.initPaymentMethodSession(configuration: PaymentMethodSessionConfiguration): PaymentMethodSession =
    PaymentMethodSession(activity, hsConfig ?: HyperswitchConfiguration(), configuration)
