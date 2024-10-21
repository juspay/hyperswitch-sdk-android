package io.hyperswitch.paymentsheet

import android.app.Application
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.jetbrains.annotations.TestOnly

/**
 * This is used internally for integrations that don't use Jetpack Compose and are
 * able to pass in an activity.
 */
internal class DefaultPaymentSheetLauncher(
    private val activityResultLauncher: ActivityResultLauncher<PaymentSheetContract.Args>,
    application: Application
) : PaymentSheetLauncher {

    constructor(
        activity: FragmentActivity, callback: PaymentSheetResultCallback
    ) : this(
        activity.registerForActivityResult(
            PaymentSheetContract()
        ) {
            callback.onPaymentSheetResult(it)
        }, activity.application
    )

    constructor(
        fragment: Fragment, callback: PaymentSheetResultCallback
    ) : this(
        fragment.registerForActivityResult(
            PaymentSheetContract()
        ) {
            callback.onPaymentSheetResult(it)
        }, fragment.requireActivity().application
    )

    @TestOnly
    constructor(
        fragment: Fragment, registry: ActivityResultRegistry, callback: PaymentSheetResultCallback
    ) : this(
        fragment.registerForActivityResult(
            PaymentSheetContract(), registry
        ) {
            callback.onPaymentSheetResult(it)
        }, fragment.requireActivity().application
    )

    override fun presentWithPaymentIntent(
        paymentIntentClientSecret: String, configuration: PaymentSheet.Configuration?
    ) = present(
        PaymentSheetContract.Args.createPaymentIntentArgsWithInjectorKey(
            paymentIntentClientSecret, configuration
        )
    )

    override fun presentWithSetupIntent(
        setupIntentClientSecret: String, configuration: PaymentSheet.Configuration?
    ) = present(
        PaymentSheetContract.Args.createSetupIntentArgsWithInjectorKey(
            setupIntentClientSecret, configuration
        )
    )

    private fun present(args: PaymentSheetContract.Args) {
        activityResultLauncher.launch(args)
    }

}