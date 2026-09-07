package io.hyperswitch

import android.app.Activity
import io.hyperswitch.lite.DefaultPaymentSessionLauncherLite
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult

/**
 * A class that manages payment sessions using a [io.hyperswitch.paymentsession.PaymentSessionLauncher].
 *
 * This class provides methods for initializing a payment session, presenting a payment sheet,
 * and retrieving customer saved payment methods.
 */
class PaymentSession internal constructor(private val paymentSessionLauncher: PaymentSessionLauncher) {

    constructor(activity: Activity, config: HyperswitchBaseConfiguration?, sessionConfig: PaymentSessionConfiguration) : this(
        DefaultPaymentSessionLauncherLite(activity, config)
    ) {
        initPaymentSession(sessionConfig)
    }

    /**
     * Initializes the payment session with the given PaymentSessionConfiguration.
     *
     * @param sessionConfig The session configuration including the client secret.
     */
    fun initPaymentSession(sessionConfig: PaymentSessionConfiguration) {
        paymentSessionLauncher.initPaymentSession(sessionConfig)
    }

    /**
     * Presents the payment sheet to the user.
     *
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(resultCallback: (PaymentResult) -> Unit) {
        paymentSessionLauncher.presentPaymentSheet(configuration = null, subscribe = null, resultCallback)
    }

    /**
     * Presents the payment sheet to the user.
     *
     * @param configuration The configuration for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configuration: PaymentSheet.Configuration, resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configuration, subscribe = null, resultCallback)
    }

    /**
     * Presents the payment sheet to the user with a configuration map.
     *
     * @param configurationMap The configuration map for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, subscribe = null, resultCallback)
    }

    /**
     * Retrieves the customer's saved payment methods.
     *
     * @param savedPaymentMethodCallback A callback that will be invoked with the customer's saved payment methods.
     */
    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)) {
        paymentSessionLauncher.getCustomerSavedPaymentMethods(savedPaymentMethodCallback)
    }
}
