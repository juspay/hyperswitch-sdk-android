package io.hyperswitch.demoapp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.PaymentSession
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import io.hyperswitch.lite.PaymentSession as PaymentSessionLite


class MainActivity : Activity() {

    lateinit var ctx: Activity;
    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""
    private lateinit var paymentSession: PaymentSession
    private lateinit var paymentSessionLite: PaymentSessionLite

    private suspend fun fetchNetceteraApiKey(): String? =
        suspendCancellableCoroutine { continuation ->
            reset().get("http://10.0.2.2:5252/netcetera-sdk-api-key")
                .responseString(object : Handler<String?> {
                    override fun success(value: String?) {
                        try {
                            val result = value?.let { JSONObject(it) }
                            val netceteraApiKey = result?.getString("netceteraApiKey")
                            continuation.resume(netceteraApiKey)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun failure(error: FuelError) {
                        continuation.resumeWithException(error)
                    }
                })
        }

    private suspend fun getCustomisations(): PaymentSheet.Configuration {
        /**
         *
         * Customisations
         *
         * */

        val primaryButtonShape = PaymentSheet.PrimaryButtonShape(32f, 0f)
        val address = PaymentSheet.Address.Builder()
            .city("city")
            .country("US")
            .line1("US")
            .line2("line2")
            .postalCode("560060")
            .state("California")
            .build()
        val billingDetails: PaymentSheet.BillingDetails = PaymentSheet.BillingDetails.Builder()
            .address(address)
            .email("email.com")
            .name("John Doe")
            .phone("1234123443").build()
        val shippingDetails = AddressDetails("Shipping Inc.", address, "6205007614", true)

        val primaryButton = PaymentSheet.PrimaryButton(
            shape = primaryButtonShape,
        )
        val color1: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = Color.parseColor("#8DBD00"),
            surface = Color.parseColor("#F5F8F9"),
        )

        val color2: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = Color.parseColor("#8DBD00"),
            surface = Color.parseColor("#F5F8F9"),
        )

        val appearance: PaymentSheet.Appearance = PaymentSheet.Appearance(
            typography = PaymentSheet.Typography(
                sizeScaleFactor = 1f,
                fontResId = R.font.montserrat
            ),
            primaryButton = primaryButton,
            colorsLight = color1,
            colorsDark = color2
        )

        val configuration = PaymentSheet.Configuration.Builder("Example, Inc.")
            .appearance(appearance)
            .defaultBillingDetails(billingDetails)
            .primaryButtonLabel("Purchase ($2.00)")
            .paymentSheetHeaderLabel("Select payment method")
            .savedPaymentSheetHeaderLabel("Payment methods")
            .shippingDetails(shippingDetails)
            .allowsPaymentMethodsRequiringShippingAddress(false)
            .allowsDelayedPaymentMethods(true)
            .displaySavedPaymentMethodsCheckbox(true)
            .displaySavedPaymentMethods(true)
            .disableBranding(true)

        try {
            val netceteraApiKey = fetchNetceteraApiKey()
            netceteraApiKey?.let {
                configuration.netceteraSDKApiKey(it)
            }
        } catch (e: Exception) {
            Log.i("Netcetera SDK API KEY ", "Key not provided in env")
        }

        return configuration.build()
    }

    private fun getCL() {

        ctx.findViewById<View>(R.id.launchButton).isEnabled = false;
        ctx.findViewById<View>(R.id.launchWebButton).isEnabled = false;
        ctx.findViewById<View>(R.id.confirmButton).isEnabled = false;

        reset().get("http://10.0.2.2:5252/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            paymentIntentClientSecret = result.getString("clientSecret")
                            publishKey = result.getString("publishableKey")

                            /**
                             *
                             * Create Payment Session Object
                             *
                             * */

                            paymentSession = PaymentSession(ctx, publishKey)
                            paymentSessionLite = PaymentSessionLite(ctx, publishKey)

                            /**
                             *
                             * Initialise Payment Session
                             *
                             * */

                            paymentSession.initPaymentSession(paymentIntentClientSecret)
                            paymentSessionLite.initPaymentSession(paymentIntentClientSecret)

                            paymentSession.getCustomerSavedPaymentMethods { it ->

                                val text =
                                    when (val data = it.getCustomerLastUsedPaymentMethodData()) {
                                        is PaymentMethod.Card -> arrayOf(
                                            data.cardScheme + " - " + data.cardNumber,
                                            true
                                        )

                                        is PaymentMethod.Wallet -> arrayOf(data.walletType, true)
                                        is PaymentMethod.Error -> arrayOf(data.message, false)
                                    }

                                setStatus("Last Used PM: " + text[0])

                                ctx.runOnUiThread {
                                    ctx.findViewById<View>(R.id.confirmButton).isEnabled = true
                                    ctx.findViewById<View>(R.id.confirmButton)
                                        .setOnClickListener { _ ->
                                            it.confirmWithCustomerLastUsedPaymentMethod {
                                                onPaymentResult(it)
                                            }
                                        }
                                }
                            }

                            ctx.runOnUiThread {
                                ctx.findViewById<View>(R.id.launchButton).isEnabled = true
                                ctx.findViewById<View>(R.id.launchWebButton).isEnabled = true
                            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ctx = this

        /**
         *
         * Merchant API call to get Client Secret
         *
         * */

        getCL()
        findViewById<View>(R.id.reloadButton).setOnClickListener { getCL() }

        /**
         *
         * Launch Payment Sheet
         *
         * */

        findViewById<View>(R.id.launchButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val customisations = getCustomisations()
                paymentSession.presentPaymentSheet(customisations, ::onPaymentSheetResult)
            }
        }

        findViewById<View>(R.id.launchWebButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                paymentSessionLite.presentPaymentSheet(
                    getCustomisations(),
                    ::onPaymentSheetResult
                )
            }
        }

    }

    private fun setStatus(error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                setStatus(paymentSheetResult.data)
            }

            is PaymentSheetResult.Failed -> {
                setStatus(paymentSheetResult.error.message ?: "")
            }

            is PaymentSheetResult.Completed -> {
                setStatus(paymentSheetResult.data)
            }
        }
    }

    private fun onPaymentResult(paymentResult: PaymentResult) {
        when (paymentResult) {
            is PaymentResult.Canceled -> {
                setStatus(paymentResult.data)
            }

            is PaymentResult.Failed -> {
                setStatus(paymentResult.throwable.message ?: "")
            }

            is PaymentResult.Completed -> {
                setStatus(paymentResult.data)
            }
        }
    }
}