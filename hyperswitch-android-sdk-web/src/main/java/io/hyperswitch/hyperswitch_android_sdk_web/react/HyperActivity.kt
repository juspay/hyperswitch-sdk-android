package io.hyperswitch.hyperswitch_android_sdk_web.react

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
//import com.facebook.react.ReactActivity
import io.hyperswitch.hyperswitch_android_sdk_web.PaymentSession
import io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet.PaymentSheet
import io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet.PaymentSheetResult

class HyperActivity : AppCompatActivity() {

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

//    override fun invokeDefaultOnBackPressed() {
//        super.onBackPressed()
//    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        PaymentSession.sheetCompletion?.let { it(paymentSheetResult) }
        finish()
    }
}