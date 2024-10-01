package io.hyperswitch.react

import android.os.Bundle
import com.facebook.react.ReactActivity
import io.hyperswitch.PaymentSession
import io.hyperswitch.paymentMethodManagementSheet.PaymentMethodManagement
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

class HyperActivity : ReactActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)
        val paymentMethodManagement = PaymentMethodManagement()

        when (intent.getIntExtra("flow", 0)) {
            1 -> paymentSheet.presentWithPaymentIntent(
                PaymentSession.paymentIntentClientSecret ?: "", PaymentSession.configuration
            )
            2 -> paymentSheet.presentWithPaymentIntentAndParams(
                PaymentSession.configurationMap ?: HashMap()
            )
            3 -> paymentMethodManagement.presentWithEphemeralKey(PaymentSession.ephemeralKey?: "")
        }

    }

    override fun invokeDefaultOnBackPressed() {
        super.onBackPressed()
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        PaymentSession.sheetCompletion?.let { it(paymentSheetResult) }
        finish()
    }
}