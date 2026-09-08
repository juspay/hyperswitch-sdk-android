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
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.OverrideEndpoints
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PMError
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
    private var pmmSession: PaymentSession? = null

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

        findViewById<View>(R.id.launchPmmSheetButton).setOnClickListener {
            // PM-session sdkAuthorizations are short-lived (~15 min) — refetch a fresh
            // session on every launch instead of reusing the one created at app start,
            // otherwise `list-payment-methods` 401s and the sheet shows an error state.
            fetchPaymentMethodSession {
                lifecycleScope.launch {
                    val result = pmmSession?.presentPaymentMethodManagement(buildConfiguration())
                    result?.let { handleResult(it) }
                }
            }
        }

        findViewById<View>(R.id.launchPmmWidgetLayout).setOnClickListener {
            startActivity(Intent(this, PaymentMethodsManagementActivity::class.java))
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
                        val profileId       = json.optString("profileId")

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
        fetchPaymentMethodSession()
    }

    // ── Payment Method Session (PMM) ───────────────────────────────────────────────────────────

    private fun fetchPaymentMethodSession(onReady: (() -> Unit)? = null) {
        runOnUiThread { findViewById<View>(R.id.launchPmmSheetButton).isEnabled = false }

        reset().post("$serverUrl/create-payment-method-session")
            .header("Content-Type" to "application/json")
            .body(PAYMENT_METHOD_SESSION_BODY)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: run {
                            // Empty body — re-enable so the launch button isn't stuck.
                            runOnUiThread { findViewById<View>(R.id.launchPmmSheetButton).isEnabled = true }
                            return
                        }
                        Log.d(TAG, "PM session response: $value")

                        val publishableKey  = json.getString("publishableKey")
                        val profileId       = json.getString("profileId")
                        val sdkAuthorization = json.getString("sdkAuthorization")

                        val pmmInstance = Hyperswitch.init(
                            activity = this@MainActivity,
                            config = HyperswitchConfiguration(
                                publishableKey = publishableKey,
                                profileId = profileId,
                                // PM sessions are created against the new gateway (see .env
                                // PM_SESSION_BASE_URL) — point every PMM API call there.
                                customConfig = CustomEndpointConfiguration(
                                    commonEndpoint = PM_SESSION_COMMON_ENDPOINT
                                ),
                            )
                        )

                        lifecycleScope.launch {
                            pmmSession = pmmInstance.initPaymentSession(
                                PaymentSessionConfiguration(sdkAuthorization = sdkAuthorization)
                            )
                            runOnUiThread {
                                findViewById<View>(R.id.launchPmmSheetButton).isEnabled = true
                                onReady?.invoke()
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse PM session response", e)
                        setStatus("Could not create payment method session")
                        // Non-JSON response (e.g. server down) — same as failure(): re-enable.
                        runOnUiThread { findViewById<View>(R.id.launchPmmSheetButton).isEnabled = true }
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "PM session request failed: ${error.message}")
                    setStatus("Could not create payment method session")
                    // Allow retrying — without this the PMM button stays disabled forever.
                    runOnUiThread { findViewById<View>(R.id.launchPmmSheetButton).isEnabled = true }
                }
            })
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

    private fun buildConfiguration(): PaymentSheet.Configuration =
        buildDemoConfiguration(netceteraApiKey)

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

        // Gateway where the mock server creates `/v1/payment-method-sessions`
        // (matches PM_SESSION_BASE_URL in .env; the SDK appends `/api` itself).
        internal const val PM_SESSION_COMMON_ENDPOINT = "https://app.hyperswitch.io"

        // Same body the RN-web demo harness uses for `create-payment-method-session`.
        // The customer_id is supplied by the mock server from the
        // PM_SESSION_CUSTOMER_ID env var — keep account-specific IDs out of the repo.
        internal const val PAYMENT_METHOD_SESSION_BODY = """
            {
              "storage_type": "persistent",
              "keep_alive": true,
              "billing": {
                "address": { "first_name": "hellow", "last_name": "world" },
                "email": "example@example.com"
              }
            }
            """
    }
}
