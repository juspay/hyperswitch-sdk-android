package io.hyperswitch.paymentsheet

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

internal class FlowControllerFactory() {

    lateinit var activity2: AppCompatActivity
    lateinit var paymentOptionCallback2: PaymentOptionCallback
    lateinit var paymentResultCallback2: PaymentSheetResultCallback

    constructor(
        activity: AppCompatActivity,
        paymentOptionCallback: PaymentOptionCallback,
        paymentResultCallback: PaymentSheetResultCallback
    ) : this() {
        activity2 = activity
        paymentOptionCallback2 = paymentOptionCallback
        paymentResultCallback2 = paymentResultCallback
    }

    constructor(
        fragment: Fragment,
        paymentOptionCallback: PaymentOptionCallback,
        paymentResultCallback: PaymentSheetResultCallback
    ) : this()

    fun create(): PaymentSheet.FlowController = DefaultFlowController(activity2, null,"",null)
}
