package io.hyperswitch.demoapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.HyperInterface
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.model.ConfirmPaymentIntentParams
import io.hyperswitch.model.PaymentMethodCreateParams
import io.hyperswitch.payments.googlepaylauncher.GooglePayEnvironment
import io.hyperswitch.payments.googlepaylauncher.GooglePayLauncher
import io.hyperswitch.payments.googlepaylauncher.GooglePayPaymentMethodLauncher
import io.hyperswitch.payments.paymentlauncher.PaymentLauncher
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.payments.paypallauncher.PayPalLauncher
import io.hyperswitch.payments.paypallauncher.PayPalPaymentMethodLauncher
import io.hyperswitch.view.CardInputWidget
import io.hyperswitch.view.GooglePayButton
import io.hyperswitch.view.PayPalButton
import org.json.JSONException
import org.json.JSONObject

class WidgetActivity : AppCompatActivity(), HyperInterface {
    lateinit var ctx: Activity;

    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""


    private lateinit var googlePayButton: GooglePayButton
    private lateinit var payPalButton: PayPalButton

    private lateinit var paymentLauncher: PaymentLauncher


    private fun setStatus(error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    private fun getCL() {

        ctx.findViewById<View>(R.id.confirmButton2).isEnabled = false;
        ctx.findViewById<View>(R.id.googlePayButton2).isEnabled = false;
        ctx.findViewById<View>(R.id.payPalButton2).isEnabled = false;


        reset().get("http://10.0.2.2:5252/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {

                            paymentIntentClientSecret = result.getString("clientSecret")
                            publishKey = result.getString("publishableKey")
                        Log.d("CLIENTSECRET",paymentIntentClientSecret+publishKey)

                                ctx.runOnUiThread {
                                    initialiseSDK()
                                    setupGooglePayLauncher()
                                    setupPayPalLauncher()
                                    ctx.findViewById<View>(R.id.confirmButton2).isEnabled = true
                                    ctx.findViewById<View>(R.id.googlePayButton2).isEnabled = true;
                                    ctx.findViewById<View>(R.id.payPalButton2).isEnabled = true;
                                }
//                            }


                        }
                    } catch (e: JSONException) {
                        Log.d("Backend Response", e.toString())
                        setStatus("could not connect to the server")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.d("Backend Response", error.toString())
                    setStatus("could not connect to the server")
                }
            })
    }

    private fun initialiseSDK () {
        /**
         *
         * Initialise Payment Configuration
         *
         * */

        PaymentConfiguration.init(applicationContext, publishKey)

        /**
         *
         * Launch Card Form
         *
         * */

        val paymentConfiguration: PaymentConfiguration = PaymentConfiguration.getInstance(
            applicationContext
        )
        paymentLauncher = PaymentLauncher.Companion.create(
            this,
            paymentConfiguration.publishableKey,
            paymentConfiguration.stripeAccountId,
            ::onPaymentResult,
        )
    }
    private fun setupGooglePayLauncher() {
        googlePayButton = findViewById(R.id.googlePayButton2)
        googlePayButton.isEnabled = false

        val googlePayLauncher = GooglePayLauncher(
            activity = this,
            config = GooglePayLauncher.Config(
                environment = GooglePayEnvironment.Test,
                merchantCountryCode = "US",
                merchantName = "Widget Store"
            ),
            readyCallback = ::onGooglePayReady,
            resultCallback = ::onGooglePayResult,
            clientSecret = paymentIntentClientSecret
        )

        googlePayButton.setOnClickListener {
            googlePayLauncher.presentForPaymentIntent(paymentIntentClientSecret)
        }
    }

    private fun setupPayPalLauncher(){
        payPalButton = findViewById(R.id.payPalButton2)
        payPalButton.isEnabled = false

        val payPalLauncher = PayPalLauncher(
            activity = this,
            readyCallback = ::onPayPalReady,
            resultCallback = ::onPayPalResult,
            clientSecret = paymentIntentClientSecret
        )

        payPalButton.setOnClickListener {
            payPalLauncher.presentForPaymentIntent(paymentIntentClientSecret)
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_activity)
        ctx = this
        getCL()

        findViewById<View>(R.id.reloadButton2).setOnClickListener { getCL() }
        findViewById<View>(R.id.confirmButton2).setOnClickListener {
            val cardInputWidget: CardInputWidget = findViewById(R.id.cardElement)
            val params: PaymentMethodCreateParams = cardInputWidget.paymentMethodCreateParams
            val confirmParams = ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                params,
                paymentIntentClientSecret
            )

            if(this::paymentLauncher.isInitialized) {
                paymentLauncher.confirm(confirmParams)
            } else {
                Toast.makeText(this, "SDK is not initialised", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPaymentResult(paymentResult: PaymentResult) {
        when (paymentResult) {
            is PaymentResult.Completed -> {
                Toast.makeText(this, paymentResult.data, Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Canceled -> {
                Toast.makeText(this, paymentResult.data, Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Failed -> {
                // This string comes from the PaymentIntent's error message.
                // See here: https://docs.hyperswitch.io/api/payment_intents/object#payment_intent_object-last_payment_error-message
                Toast.makeText(this, paymentResult.throwable.message ?: "", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun onGooglePayReady(isReady: Boolean) {
        googlePayButton.isEnabled = isReady
    }

    private fun onGooglePayResult(
        result: GooglePayPaymentMethodLauncher.Result
    ) {
        when (result) {
            is GooglePayPaymentMethodLauncher.Result.Completed -> {
                // Payment details successfully captured.
                // Send the paymentMethodId to your server to finalize payment.
                val paymentMethodId = result.paymentMethod.id
                Toast.makeText(this, paymentMethodId, Toast.LENGTH_LONG).show()
            }
            is GooglePayPaymentMethodLauncher.Result.Canceled -> {
                // User canceled the operation
                Toast.makeText(this, result.data, Toast.LENGTH_LONG).show()
            }
            is GooglePayPaymentMethodLauncher.Result.Failed -> {
                // Operation failed; inspect `result.error` for the exception
                Toast.makeText(this, result.error.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onPayPalReady(isReady: Boolean) {
        payPalButton.isEnabled = isReady
    }

    private fun onPayPalResult(
        result: PayPalPaymentMethodLauncher.Result
    ) {
        when (result) {
            is PayPalPaymentMethodLauncher.Result.Completed -> {
                // Payment details successfully captured.
                // Send the paymentMethodId to your server to finalize payment.
                val paymentMethodId = result.paymentMethod.id
                Toast.makeText(applicationContext, paymentMethodId, Toast.LENGTH_LONG).show()
            }
            is PayPalPaymentMethodLauncher.Result.Canceled -> {
                // User canceled the operation
                Toast.makeText(applicationContext, result.data, Toast.LENGTH_LONG).show()
            }
            is PayPalPaymentMethodLauncher.Result.Failed -> {
                // Operation failed; inspect `result.error` for the exception
                Toast.makeText(applicationContext, result.error.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}