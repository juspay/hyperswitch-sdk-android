package io.hyperswitch.demoapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PMError
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.sdk.PaymentSession
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity(), HyperInterface {

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    private var netceteraApiKey: String? = null
    private var serverUrl = DEFAULT_SERVER_URL
    private var hyperswitchInstance: HyperswitchInstance? = null
    private var paymentSession: PaymentSession? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        serverUrl = loadServerUrl()
        findViewById<EditText>(R.id.ipAddressInput).apply {
            setText(serverUrl)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val url = s?.toString().orEmpty()
                    if (url.isNotEmpty()) updateServerUrl(url)
                }
            })
        }

        fetchPaymentIntent()

        findViewById<View>(R.id.reloadButton).setOnClickListener { fetchPaymentIntent() }

        findViewById<View>(R.id.launchButton).setOnClickListener {
            lifecycleScope.launch {
                val result = paymentSession?.presentPaymentSheet(buildConfiguration())
                result?.let { handleResult(it) }
            }
        }

        findViewById<View>(R.id.launchWidgetLayout).setOnClickListener {
            startActivity(Intent(this, WidgetActivity::class.java))
        }
    }

    // ── Backend calls ──────────────────────────────────────────────────────────────────────────

    private fun fetchPaymentIntent() {
        setButtonsEnabled(launch = false, confirm = false)

        reset().get("$serverUrl/create-payment-intent")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: return
                        Log.d(TAG, "Backend response: $value")

                        val publishableKey  = json.getString("publishableKey")
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        val profileId       = json.optString("profileId").takeIf { it.isNotEmpty() }

                        hyperswitchInstance = Hyperswitch.init(
                            activity = this@MainActivity,
                            config = HyperswitchConfiguration(
                                publishableKey = publishableKey,
                                profileId = profileId,
                            )
                        )

                        lifecycleScope.launch {
                            paymentSession = hyperswitchInstance?.initPaymentSession(
                                PaymentSessionConfiguration(sdkAuthorization = sdkAuthorization)
                            )
                            onSessionReady()
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse backend response", e)
                        setStatus("Could not connect to the server")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "Backend request failed: ${error.message}")
                    setStatus("Could not connect to the server")
                }
            })

        fetchNetceteraApiKey()
    }

    private fun fetchNetceteraApiKey() {
        reset().get("$serverUrl/netcetera-sdk-api-key")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    runCatching { netceteraApiKey = value?.let { JSONObject(it) }?.getString("netceteraApiKey") }
                }
                override fun failure(error: FuelError) = Unit
            })
    }

    // ── Session ready ──────────────────────────────────────────────────────────────────────────

    /** Called on the main thread once [paymentSession] is fully initialised. */
    private fun onSessionReady() {
        setButtonsEnabled(launch = true, confirm = false)

        paymentSession?.getCustomerSavedPaymentMethods { handler ->
            val text = handler.getCustomerLastUsedPaymentMethodData().fold(
                onSuccess = { data ->
                    data.card?.let { "${it.scheme} - ${it.last4Digits}" } ?: data.paymentMethodType
                },
                onFailure = { error -> (error as? PMError)?.message ?: "Unknown error" }
            )
            setStatus("Last used: $text")

            runOnUiThread {
                setButtonsEnabled(launch = true, confirm = true)
                findViewById<View>(R.id.confirmButton).setOnClickListener {
                    handler.confirmWithCustomerLastUsedPaymentMethod { handleResult(it) }
                }
            }
        }
    }

    // ── Configuration ──────────────────────────────────────────────────────────────────────────

    private fun buildConfiguration(): PaymentSheet.Configuration {
        val address = PaymentSheet.Address.Builder()
            .city("city").country("US").line1("US").line2("line2")
            .postalCode("560060").state("California").build()

        val billingDetails = PaymentSheet.BillingDetails.Builder()
            .address(address).email("email.com").name("John Doe").phone("1234123443").build()

        val shippingDetails = AddressDetails("Shipping Inc.", address, "6205007614", true)

        val appearance = PaymentSheet.Appearance(theme = PaymentSheet.Theme.Light)

        val wallets = PaymentSheet.WalletConfiguration(
            googlePay = PaymentSheet.WalletShowType.Auto,
            style = PaymentSheet.WalletStyle(theme = PaymentSheet.WalletTheme.Dark, height = 52),
        )

        return PaymentSheet.Configuration.Builder("Example, Inc.")
            .appearance(appearance)
            .wallets(wallets)
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
            .also { builder -> netceteraApiKey?.let { builder.netceteraSDKApiKey(it) } }
            .build()
    }

    // ── Result handling ────────────────────────────────────────────────────────────────────────

    private fun handleResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Completed -> setStatus("Completed: ${result.data}")
            is PaymentResult.Canceled  -> setStatus("Cancelled: ${result.data}")
            is PaymentResult.Failed    -> setStatus("Failed: ${result.throwable.message.orEmpty()}")
        }
    }

    // ── Server URL helpers ─────────────────────────────────────────────────────────────────────

    private fun updateServerUrl(newUrl: String) {
        if (Patterns.WEB_URL.matcher(newUrl).matches()) {
            serverUrl = newUrl
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putString(KEY_SERVER_URL, newUrl) }
            setStatus("Reload to apply new server URL")
        } else {
            setStatus("Invalid URL format")
        }
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    // ── UI helpers ─────────────────────────────────────────────────────────────────────────────

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }

    private fun setButtonsEnabled(launch: Boolean, confirm: Boolean) {
        runOnUiThread {
            findViewById<View>(R.id.launchButton).isEnabled = launch
            findViewById<View>(R.id.confirmButton).isEnabled = confirm
        }
    }

    // ── Constants ──────────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }
}
