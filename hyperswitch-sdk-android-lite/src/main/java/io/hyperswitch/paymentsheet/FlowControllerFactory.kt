package io.hyperswitch.paymentsheet

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

internal class FlowControllerFactory(
    private val activity: FragmentActivity,
    private val paymentOptionCallback: PaymentOptionCallback,
    private val paymentResultCallback: PaymentSheetResultCallback
) {
    constructor(
        fragment: Fragment,
        paymentOptionCallback: PaymentOptionCallback,
        paymentResultCallback: PaymentSheetResultCallback
    ) : this(
        activity = fragment.requireActivity(),
        paymentOptionCallback = paymentOptionCallback,
        paymentResultCallback = paymentResultCallback,
    )

    fun create(): PaymentSheet.FlowController =
        DefaultFlowController(activity, null, "", null)
}