package io.hyperswitch.demoapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.bridge.Callback
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.CVCWidget
import io.hyperswitch.HyperInterface
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentWidget
import org.json.JSONException
import org.json.JSONObject

/**
 * WidgetActivity demonstrates how to use PaymentWidget and CVCWidget
 * for embedding payment forms in your Android application.
 *
 * Based on React Native SDK initialization pattern from PaymentSheet.tsx:
 * - PaymentConfiguration.init() - SDK setup with publishableKey
 * - PaymentWidget.initWidget() - Widget initialization
 * - PaymentWidget.setSdkAuthorization() - Set payment intent token
 * - PaymentWidget.confirmPayment() - Trigger payment confirmation
 *
 * For saved cards (CVCWidget):
 * - Same initialization pattern as PaymentWidget
 * - CVCWidget.confirmCvcPayment() - Confirm with paymentToken and paymentMethodId
 */
class WidgetActivity : AppCompatActivity(), HyperInterface {
    lateinit var ctx: Activity

    private var sdkAuthorization: String = ""
    private var publishableKey: String = ""

    // PaymentWidget reference - holds the main payment form (card input)
    private lateinit var paymentWidget: PaymentWidget

    // CVCWidget reference - holds the CVC input for saved cards (optional)
    private var cvcWidget: CVCWidget? = null

    // Saved payment method data (for CVC payments)
    private var paymentToken: String? = null
    private var paymentMethodId: String? = null

    private fun setStatus(error: String = "could not connect to the server") {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    private fun getCL() {
        ctx.findViewById<View>(R.id.confirmButton2).isEnabled = false

        reset().get("http://10.0.2.2:5252/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            sdkAuthorization = result.getString("sdkAuthorization")
                            publishableKey = result.getString("publishableKey")

                            // Extract saved payment method data if available
                            if (result.has("paymentToken") && result.has("paymentMethodId")) {
                                paymentToken = result.optString("paymentToken")
                                paymentMethodId = result.optString("paymentMethodId")
                            }

                            ctx.runOnUiThread {
                                initialiseWidgets()
                                ctx.findViewById<View>(R.id.confirmButton2).isEnabled = true
                            }
                        }
                    } catch (e: JSONException) {
                        setStatus()
                    }
                }

                override fun failure(error: FuelError) {
                    setStatus()
                }
            })
    }

    private fun initialiseWidgets() {
        /**
         * Step 1: Initialise Payment Configuration with publishableKey
         * This is required before using any widget
         */
        PaymentConfiguration.init(applicationContext, publishableKey)

        /**
         * Step 2: Initialise PaymentWidget
         * - initWidget() initializes the internal view
         * - setSdkAuthorization() sets the payment intent token
         */
        paymentWidget = findViewById(R.id.paymentSheet)
        paymentWidget.setSdkAuthorization(sdkAuthorization)

        cvcWidget = findViewById(R.id.cvcWidget)
        cvcWidget?.setSdkAuthorization(sdkAuthorization)

        /**
         * Step 3: Initialise CVCWidget (optional - for saved card payments)
         * Same initialization pattern as PaymentWidget
         */
//        val cvcWidgetView = findViewById<CVCWidget?>(R.id.cvcWidget)
//        if (cvcWidgetView != null) {
//            cvcWidget = cvcWidgetView
//            cvcWidget?.initWidget(publishableKey)
//            cvcWidget?.setSdkAuthorization(sdkAuthorization)
//        }

        /**
         * Step 4: Setup button click listeners
         */
        findViewById<View>(R.id.confirmButton2).setOnClickListener {
            confirmPayment()
        }
    }

    /**
     * Confirm payment using PaymentWidget
     * Use this for new card payments
     */
    private fun confirmPayment() {
        val callback = Callback { args ->
            val result = args[0] as? String ?: ""
            handlePaymentResult(result)
        }
        paymentWidget.confirmPayment(callback)
    }

    /**
     * Confirm CVC payment using CVCWidget
     * Use this for saved card payments where CVC is required
     *
     * @param paymentToken The payment token for the saved card
     * @param paymentMethodId The payment method ID for the saved card
     */
    private fun confirmCvcPayment(paymentToken: String, paymentMethodId: String) {
        val cvcWidgetRef = cvcWidget
        if (cvcWidgetRef == null) {
            Toast.makeText(this, "CVC Widget not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val callback: (Array<out Any?>) -> Unit = { args ->
            val result = args[0] as? String ?: ""
            handlePaymentResult(result)
        }

//        cvcWidgetRef.confirmCvcPayment(callback, paymentToken, paymentMethodId)
    }

    /**
     * Parse and handle payment result from widget callbacks
     */
    private fun handlePaymentResult(result: String) {
        runOnUiThread {
            try {
                val jsonObject = JSONObject(result)
                val status = jsonObject.optString("status", "unknown")
                val message = jsonObject.optString("message", "")

                when (status) {
                    "succeeded" -> {
                        Toast.makeText(this, "Payment Successful: $message", Toast.LENGTH_LONG).show()
                    }
                    "failed", "requires_payment_method" -> {
                        Toast.makeText(this, "Payment Failed: $message", Toast.LENGTH_LONG).show()
                    }
                    "cancelled" -> {
                        Toast.makeText(this, "Payment Cancelled", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Status: $status", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: JSONException) {
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_activity)
        ctx = this
        getCL()

        findViewById<View>(R.id.reloadButton2).setOnClickListener { getCL() }
    }
}
