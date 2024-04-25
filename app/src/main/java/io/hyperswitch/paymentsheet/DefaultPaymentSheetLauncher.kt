package io.hyperswitch.paymentsheet

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.facebook.react.bridge.Callback
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.payments.gpay.GooglePayActivity
import io.hyperswitch.react.Utils
import org.json.JSONObject


/**
 * This is used internally for integrations that don't use Jetpack Compose and are
 * able to pass in an activity.
 */
internal class DefaultPaymentSheetLauncher(
    private val activityResultLauncher: ActivityResultLauncher<PaymentSheetContract.Args>,
    application: Application
) : PaymentSheetLauncher {

    companion object {
        lateinit var context : AppCompatActivity
        lateinit var onPaymentSheetResult: PaymentSheetResultCallback
        @JvmStatic lateinit var googlePayCallback: Callback

        fun paymentResultCallback(paymentResult: String, reset: Boolean) {
            Utils.hideFragment(context, reset)
            val jsonObject = JSONObject(paymentResult)
            when (val status = jsonObject.getString("status")) {
                "cancelled" -> onPaymentSheetResult.onPaymentSheetResult(PaymentSheetResult.Canceled(status))
                "failed", "requires_payment_method" -> {
                    val throwable = Throwable(jsonObject.getString("message"))
                    throwable.initCause(Throwable(jsonObject.getString("code")))
                    onPaymentSheetResult.onPaymentSheetResult(PaymentSheetResult.Failed(throwable))
                }
                else -> onPaymentSheetResult.onPaymentSheetResult(PaymentSheetResult.Completed(status))
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun gPayWalletCall(gPayRequest: String, callback: Callback) {
            googlePayCallback = callback
            val myIntent = Intent(
                context,
                GooglePayActivity::class.java
            )
            myIntent.putExtra("gPayRequest", gPayRequest)
            context.startActivity(myIntent)
        }
    }

    constructor(
        activity: AppCompatActivity,
        callback: PaymentSheetResultCallback
    ) : this(
        activity.registerForActivityResult(
            PaymentSheetContract()
        ) {
            callback.onPaymentSheetResult(it)
        },
        activity.application
    ) {
        context = activity
        onPaymentSheetResult = callback
    }

    constructor(
        fragment: Fragment,
        callback: PaymentSheetResultCallback
    ) : this(
        fragment.registerForActivityResult(
            PaymentSheetContract()
        ) {
            callback.onPaymentSheetResult(it)
        },
        fragment.requireActivity().application
    )

    override fun presentWithPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ) = present(paymentIntentClientSecret, configuration, null)

    override fun presentWithPaymentIntentAndParams(
        map: Map<String, Any?>,
        sheetType: String?
    ) = presentWithParams(map, null)

    override fun presentWithNewPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ) = present(paymentIntentClientSecret, configuration, "hostedCheckout")

    override fun presentWithSetupIntent(
        setupIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ) = present(setupIntentClientSecret, configuration, null)

    private fun present(
        clientSecret: String,
        configuration: PaymentSheet.Configuration?,
        sheetType: String?
    ) {
        context.onBackPressedDispatcher.addCallback(context) {
            isEnabled = Utils.onBackPressed()
            if(!isEnabled) context.onBackPressedDispatcher.onBackPressed()
        }
        val map = mapOf(
            "publishableKey" to PaymentConfiguration.pkKey,
            "clientSecret" to clientSecret,
            "customBackendUrl" to PaymentConfiguration.cbUrl,
            "customLogUrl" to PaymentConfiguration.logUrl,
            "hyperParams" to mapOf("disableBranding" to configuration?.disableBranding, "defaultView" to configuration?.defaultView),
            "themes" to configuration?.themes,
            "customParams" to PaymentConfiguration.cParams,
            "configuration" to configuration?.getMap()
        )
        Utils.openReactView(context, map, sheetType ?: "payment", null)
    }

    private fun presentWithParams(
        map: Map<String, Any?>,
        sheetType: String?
    ) {
        context.onBackPressedDispatcher.addCallback(context) {
            isEnabled = Utils.onBackPressed()
            if(!isEnabled) context.onBackPressedDispatcher.onBackPressed()
        }
        Utils.openReactView(context, map, sheetType ?: "payment", null)
    }
}