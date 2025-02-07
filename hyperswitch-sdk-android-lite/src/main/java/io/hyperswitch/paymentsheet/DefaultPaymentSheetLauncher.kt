package io.hyperswitch.paymentsheet

import android.app.Activity
import android.app.Application
import android.app.Fragment

/**
 * This is used internally for integrations that don't use Jetpack Compose and are
 * able to pass in an activity.
 */
internal class DefaultPaymentSheetLauncher(
    // private val activityResultLauncher: ActivityResultLauncher<PaymentSheetContract.Args>,
    application: Application
) : PaymentSheetLauncher {

    constructor(
        activity: Activity, callback: PaymentSheetResultCallback
    ) : this(
        activity.application
    )

    constructor(
        fragment: Fragment, callback: PaymentSheetResultCallback
    ) : this(
        fragment.activity.application
    )

    override fun presentWithPaymentIntent(
        paymentIntentClientSecret: String, configuration: PaymentSheet.Configuration?
    ) = present()

    override fun presentWithSetupIntent(
        setupIntentClientSecret: String, configuration: PaymentSheet.Configuration?
    ) = present()

    private fun present() {
        // activityResultLauncher.launch(args)
    }

}