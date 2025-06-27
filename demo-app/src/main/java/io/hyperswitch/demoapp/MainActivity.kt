package io.hyperswitch.demoapp

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.util.Patterns
import android.graphics.Color
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
import androidx.core.content.edit


class MainActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }

    lateinit var ctx: Activity;
    private var paymentIntentClientSecret: String = "clientSecret"
    private var ephemeralKey: String = "ephemeralKey"
    private var paymentMethodClientSecret: String = "clientSecret"

    private var publishKey: String = ""
    private var serverUrl = "http://10.0.2.2:5252"
    private lateinit var paymentSession: PaymentSession
    private lateinit var paymentSessionLite: PaymentSessionLite
    private lateinit var editText : EditText


    private suspend fun fetchNetceteraApiKey(): String? =
        suspendCancellableCoroutine { continuation ->
            reset().get("$serverUrl/netcetera-sdk-api-key")
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

    private fun getSharedPreferences(): android.content.SharedPreferences {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences().edit { putString(KEY_SERVER_URL, url) }
    }

    private fun loadServerUrl(): String {
        return getSharedPreferences().getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
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
            .showVersionInfo(true)
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

        reset().get("$serverUrl/create-payment-intent", null)
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
        editText = ctx.findViewById<EditText>(R.id.ipAddressInput)
        // Load saved URL or use default
        serverUrl = loadServerUrl()
        editText.setText(serverUrl)

        // Add TextWatcher for URL validation
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
        findViewById<View>(R.id.reloadButton).setOnClickListener { getCL()
        }

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

        findViewById<View>(R.id.launchWidgetLayout).setOnClickListener{
            val intent = Intent(this,WidgetActivity::class.java)
            startActivity(intent)
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