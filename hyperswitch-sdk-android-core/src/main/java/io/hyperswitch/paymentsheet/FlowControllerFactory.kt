package io.hyperswitch.paymentsheet

import android.app.Activity
import android.app.Fragment

internal class FlowControllerFactory(
    private val activity: Activity,
    private val paymentOptionCallback: PaymentOptionCallback,
    private val paymentResultCallback: PaymentSheetResultCallback
) {
    constructor(
        fragment: Fragment,
        paymentOptionCallback: PaymentOptionCallback,
        paymentResultCallback: PaymentSheetResultCallback
    ) : this(
        activity = fragment.activity,
        paymentOptionCallback = paymentOptionCallback,
        paymentResultCallback = paymentResultCallback,
    )

    fun create(): PaymentSheet.FlowController =
        DefaultFlowController(activity, null, "", null)
}