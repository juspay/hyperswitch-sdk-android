package io.hyperswitch.react

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.HyperInterface
import io.hyperswitch.PaymentSession
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult

class HyperActivity : AppCompatActivity(), HyperInterface {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        when (intent.getIntExtra("flow", 0)) {
            1 -> paymentSheet.presentWithPaymentIntent(
                PaymentSession.paymentIntentClientSecret ?: "", PaymentSession.configuration
            )
            2 -> paymentSheet.presentWithPaymentIntentAndParams(
                PaymentSession.configurationMap ?: HashMap()
            )
        }

    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        PaymentSession.sheetCompletion?.let { it(paymentSheetResult) }
        finish()
    }
}