package io.hyperswitch.demoapp

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
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//import androidx.core.graphics.toColorIntimport java.io.File


class MainActivity : Activity() {
    lateinit var ctx: Activity
    private var publishableKey: String = ""
    private var paymentIntentClientSecret: String = "clientSecret"
    private var netceteraApiKey: String? = null
    private val prefsName = "HyperswitchPrefs"
    private val keyServerUrl = "server_url"
    private var serverUrl = "http://10.0.2.2:5252"
    private lateinit var paymentSession: PaymentSession
//    private lateinit var paymentSessionLite: PaymentSessionLite
    private lateinit var editText: EditText

    // Performance tracking
    private lateinit var mainSdkPerfTracker: SDKPerformanceTracker
    private lateinit var perfExporter: PerformanceExporter
    private var mainSdkMetrics: SDKPerformanceTracker.SDKMetrics? = null

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
                sizeScaleFactor = 1f, fontResId = R.font.montserrat
            ),
            primaryButton = primaryButton,
            colorsLight = color1,
            colorsDark = color2,
            theme = PaymentSheet.Theme.Light
        )

        val configuration =
            PaymentSheet.Configuration.Builder("Example, Inc.")
                //.appearance(appearance)
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

//         ctx.findViewById<View>(R.id.launchButton).isEnabled = false
// //        ctx.findViewById<View>(R.id.launchWebButton).isEnabled = false
//         ctx.findViewById<View>(R.id.confirmButton).isEnabled = false

//         // Track API call latency
        val apiStartTime = android.os.SystemClock.elapsedRealtime()
        
        ctx.findViewById<View>(R.id.launchButton).isEnabled = false
        ctx.findViewById<View>(R.id.confirmButton).isEnabled = false

        reset().get("$serverUrl/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        // Record API latency
                        val apiLatency = android.os.SystemClock.elapsedRealtime() - apiStartTime
//                        mainSdkApiTracker.getLatencies() // This ensures the list is initialized
                        Log.d("SDK_PERF", "Create Payment Intent API took ${apiLatency}ms")

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
                             * Initialise Payment Session (with init time tracking)
                             *
                             * */

                            // Track Main SDK initialization
//                            mainSdkPerfTracker.startInitTracking()
                            paymentSession.initPaymentSession(paymentIntentClientSecret)
//                            mainSdkPerfTracker.endInitTracking()


                            paymentSession.getCustomerSavedPaymentMethods { it ->
                                val text =
                                    when (val data = it.getCustomerLastUsedPaymentMethodData()) {
                                        is PaymentMethod.PaymentMethodType ->
                                            data.card?.let { "${it.scheme} - ${it.last4Digits}" }
                                            ?: data.paymentMethodType
                                        is PaymentMethod.Error -> data.message
                                    }

                                setStatus("Last Used PM: $text")

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

        // Initialize performance trackers and exporter BEFORE getCL()
        mainSdkPerfTracker = SDKPerformanceTracker(ctx)
        perfExporter = PerformanceExporter(ctx)

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
        findViewById<View>(R.id.reloadButton).setOnClickListener {
            getCL()
        }

        /**
         *
         * Launch Main Payment Sheet (with performance tracking)
         *
         * */

        findViewById<View>(R.id.launchButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                // Capture button click time for performance tracking (Unix timestamp)
                val buttonClickTime = System.currentTimeMillis()
                Log.d("SDK_PERF_CLICK", "Main SDK button clicked at: $buttonClickTime")

                // Start tracking BEFORE opening SDK
                mainSdkPerfTracker.startTracking()
                Log.d("SDK_PERF", "Started tracking Main SDK")

                val customisations = getCustomisations()
                paymentSession.presentPaymentSheet(customisations, ::onMainSdkPaymentSheetResult)
            }
        }

        findViewById<View>(R.id.launchWidgetLayout).setOnClickListener {
            val intent = Intent(this, WidgetActivity::class.java)
            startActivity(intent)
        }

    }

    private fun setStatus(error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    /**
     * Main SDK Payment Sheet Result with Performance Tracking
     */
    private fun onMainSdkPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        // Stop tracking AFTER SDK completes
        val metrics = mainSdkPerfTracker.stopTracking("Main SDK")
        mainSdkMetrics = metrics

        // Export as JSON only
        val json = perfExporter.exportAsJson(metrics)
        perfExporter.saveToFile(json, "main_sdk_${System.currentTimeMillis()}.json")

        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                setStatus("Main SDK - ${paymentSheetResult.data}")
            }

            is PaymentSheetResult.Failed -> {
                setStatus("Main SDK - ${paymentSheetResult.error.message ?: ""}")
            }

            is PaymentSheetResult.Completed -> {
                setStatus("Main SDK - ${paymentSheetResult.data}")
            }
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