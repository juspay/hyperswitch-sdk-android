package io.hyperswitch

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.PaymentSessionLauncher
import io.hyperswitch.paymentsheet.PaymentSheet.Configuration
import io.hyperswitch.paymentsheet.PaymentSheetResult

/**
 * A class that manages payment sessions using a [PaymentSessionLauncher].
 *
 * This class provides methods for initializing a payment session, presenting a payment sheet,
 * and retrieving customer saved payment methods.
 */
class PaymentSession internal constructor(private val paymentSessionLauncher: PaymentSessionLauncher) {
    constructor(activity: Activity, publishableKey: String, profileId: String) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, profileId, null, null, null)
    )

    constructor(
        activity: Activity, publishableKey: String, profileId: String, customBackendUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(activity, publishableKey, profileId, customBackendUrl, null, null)
    )

    constructor(
        activity: Activity,
        publishableKey: String,
        profileId: String,
        customBackendUrl: String,
        customLogUrl: String
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, profileId, customBackendUrl, customLogUrl, null
        )
    )
    // The old constructors without profileId were removed because profile_id is now mandatory. Any integration using the old constructors was already broken at runtime — the payment sheet guard in NavigationRouter blocks all API calls when 
   // profileId is empty. By removing these constructors, we shift the failure from a silent runtime issue to a compile-time error, which is much easier for merchants to debug. This is a breaking change but only breaks integrations that were already non-functional.


    constructor(
        activity: Activity,
        publishableKey: String,
        profileId: String,
        customBackendUrl: String,
        customLogUrl: String,
        customParams: Bundle
    ) : this(
        DefaultPaymentSessionLauncher(
            activity, publishableKey, profileId, customBackendUrl, customLogUrl, customParams
        )
    )

    /*** A builder class for creating instances of [PaymentSession].
     *
     * @param activity The activity that will host the payment sheet.
     * @param publishableKey The publishable key for your Stripe account.
     * @param profileId The profile ID for the payment session.
     */
    class Builder(private val activity: Activity, private val publishableKey: String, private val profileId: String) {
        private var customBackendUrl: String? = null
        private var customLogUrl: String? = null
        private var customParams: Bundle? = null

        fun customBackendUrl(url: String) = apply { this.customBackendUrl = url }
        fun customLogUrl(url: String) = apply { this.customLogUrl = url }
        fun customParams(params: Bundle) = apply { this.customParams = params }

        fun build(): PaymentSession {
            val launcher = DefaultPaymentSessionLauncher(
                activity, publishableKey, profileId, customBackendUrl, customLogUrl, customParams
            )
            return PaymentSession(launcher)
        }
    }

    /**
     * Initializes the payment session with the given payment intent client secret.
     *
     * @param paymentIntentClientSecret The client secret of the payment intent.
     */
    fun initPaymentSession(paymentIntentClientSecret: String) {
        paymentSessionLauncher.initPaymentSession(paymentIntentClientSecret)
    }

    /**
     * Presents the payment sheet to the user.
     *
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(resultCallback: (PaymentSheetResult) -> Unit) {
        paymentSessionLauncher.presentPaymentSheet(configuration = null, resultCallback)
    }

    /**
     * Presents the payment sheet to the user.
     *
     * @param configuration The configuration for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configuration: Configuration, resultCallback: (PaymentSheetResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configuration, resultCallback)
    }

    /**
     * Presents the payment sheet to the user with a configuration map.
     *
     * @param configurationMap The configuration map for the payment sheet.
     * @param resultCallback A callback that will be invoked when the payment sheet is closed.
     */
    fun presentPaymentSheet(
        configurationMap: Map<String, Any?>,
        resultCallback: (PaymentSheetResult) -> Unit
    ) {
        paymentSessionLauncher.presentPaymentSheet(configurationMap, resultCallback)
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