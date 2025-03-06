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
import io.hyperswitch.payments.paymentlauncher.PaymentLauncher
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.view.CardInputWidget
import org.json.JSONException
import org.json.JSONObject

class WidgetActivity : AppCompatActivity(), HyperInterface {
    lateinit var ctx: Activity;

    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""

    private lateinit var paymentLauncher: PaymentLauncher

    private fun setStatus(error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    private fun getCL() {

        ctx.findViewById<View>(R.id.confirmButton2).isEnabled = false;


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
                                    ctx.findViewById<View>(R.id.confirmButton2).isEnabled = true
                                }
                            initialiseSDK()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_activity)
        ctx = this
        
        getCL()



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

            findViewById<View>(R.id.reloadButton2).setOnClickListener { getCL() }
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


}