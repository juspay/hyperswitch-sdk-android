package io.hyperswitch.demo_app_lite

import android.app.Activity
import android.content.Intent
import android.util.Patterns
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.PaymentSession
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

class MainActivity : Activity() {
    lateinit var ctx: Activity
    private var publishableKey: String = ""
    private var paymentIntentClientSecret: String = "clientSecret"
    private var netceteraApiKey: String? = null
    private val prefsName = "HyperswitchPrefs"
    private val keyServerUrl = "server_url"
    private var serverUrl = "http://10.0.2.2:5252"
    private lateinit var paymentSession: PaymentSession
    private lateinit var editText: EditText

    private fun fetchNetceteraApiKey() = {
        reset().get("$serverUrl/netcetera-sdk-api-key").responseString(object : Handler<String?> {
            override fun success(value: String?) {
                try {
                    val result = value?.let { JSONObject(it) }
                    netceteraApiKey = result?.getString("netceteraApiKey")
                } catch (_: Exception) {
                }
            }

            override fun failure(error: FuelError) {}
        })
    }

    private fun getSharedPreferences(): android.content.SharedPreferences {
        return ctx.getSharedPreferences(prefsName, MODE_PRIVATE)
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences().edit { putString(keyServerUrl, url) }
    }

    private fun loadServerUrl(): String {
        return getSharedPreferences().getString(keyServerUrl, serverUrl)
            ?: serverUrl
    }

    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    private fun updateServerUrl(newUrl: String) {
        if (isValidUrl(newUrl)) {
            serverUrl = newUrl
            saveServerUrl(newUrl)
            setStatus("Reload Client Secret")
        } else {
            setStatus("Invalid URL format")
        }
    }

    private fun getCustomisations(): PaymentSheet.Configuration {
        /**
         *
         * Customisations
         *
         * */

        val primaryButtonShape = PaymentSheet.PrimaryButtonShape(32f, 0f)
        val address =
            PaymentSheet.Address.Builder().city("city").country("US").line1("US").line2("line2")
                .postalCode("560060").state("California").build()
        val billingDetails: PaymentSheet.BillingDetails =
            PaymentSheet.BillingDetails.Builder().address(address).email("email.com")
                .name("John Doe").phone("1234123443").build()
        val shippingDetails = AddressDetails("Shipping Inc.", address, "6205007614", true)

        val primaryButton = PaymentSheet.PrimaryButton(
            shape = primaryButtonShape,
        )
        val color1: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = "#8DBD00".toColorInt(),
            surface = "#F5F8F9".toColorInt(),
        )

        val color2: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = "#8DBD00".toColorInt(),
            surface = "#F5F8F9".toColorInt(),
        )

        val appearance: PaymentSheet.Appearance = PaymentSheet.Appearance(
            typography = PaymentSheet.Typography(
                sizeScaleFactor = 1f, fontFamily = "montserrat"
            ),
            primaryButton = primaryButton,
            colorsLight = color1,
            colorsDark = color2,
            theme = PaymentSheet.Theme.Light
        )

        val configuration =
            PaymentSheet.Configuration.Builder("Example, Inc.").appearance(appearance)
                .defaultBillingDetails(billingDetails).primaryButtonLabel("Purchase ($2.00)")
                .paymentSheetHeaderLabel("Select payment method")
                .savedPaymentSheetHeaderLabel("Payment methods").shippingDetails(shippingDetails)
                .allowsPaymentMethodsRequiringShippingAddress(false)
                .allowsDelayedPaymentMethods(true).displaySavedPaymentMethodsCheckbox(true)
                .displaySavedPaymentMethods(true).disableBranding(true).showVersionInfo(true)

//        netceteraApiKey?.let {
//            configuration.netceteraSDKApiKey(it)
//        }

        return configuration.build()
    }

    private fun getCL() {

        ctx.findViewById<View>(R.id.launchButton).isEnabled = false

        reset().get("$serverUrl/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            paymentIntentClientSecret = result.getString("clientSecret")
                            publishableKey = result.getString("publishableKey")

                            /**
                             *
                             * Create Payment Session Object
                             *
                             * */

                            paymentSession = PaymentSession(ctx, publishableKey)

                            /**
                             *
                             * Initialise Payment Session
                             *
                             * */

                            paymentSession.initPaymentSession(paymentIntentClientSecret)

                            ctx.runOnUiThread {
                                ctx.findViewById<View>(R.id.launchButton).isEnabled = true
                            }
                        }
                    } catch (e: JSONException) {
                        Log.d("Backend Response", e.toString())
                        setStatus("could not connect to the server")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.d("Backend Response", error.message ?: "")
                    setStatus("could not connect to the server")
                }
            })

        fetchNetceteraApiKey()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ctx = this
        editText = ctx.findViewById(R.id.ipAddressInput)
        serverUrl = loadServerUrl()
        editText.setText(serverUrl)
        ctx.findViewById<View>(R.id.openC2P).setOnClickListener {
            val intent = Intent(ctx, ClickToPayExample::class.java)
            startActivity(intent)
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { newUrl ->
                    if (newUrl.isNotEmpty()) {
                        updateServerUrl(newUrl)
                    }
                }
            }
        })

        /**
         *
         * Merchant API call to get Client Secret
         *
         * */

        getCL()
        findViewById<View>(R.id.reloadButton).setOnClickListener {
            getCL()
        }

        /**
         *
         * Launch Payment Sheet
         *
         * */

        findViewById<View>(R.id.launchButton).setOnClickListener {
            val customisations = getCustomisations()
            paymentSession.presentPaymentSheet(customisations, ::onPaymentSheetResult)
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
}