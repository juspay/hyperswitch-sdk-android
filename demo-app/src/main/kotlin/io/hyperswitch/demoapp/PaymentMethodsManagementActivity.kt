package io.hyperswitch.demoapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchBoundElement
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.view.PaymentMethodsManagementElement
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * Hosts the embeddable [PaymentMethodsManagementElement] widget: lists the
 * customer's saved payment methods and lets them add/remove cards.
 *
 * Requires a payment method session (`POST /create-payment-method-session`
 * on the demo mock server) — the returned `sdkAuthorization` authorizes
 * every PMM API call the SDK makes.
 */
class PaymentMethodsManagementActivity : AppCompatActivity(), HyperInterface {

    private var hyperswitchInstance: HyperswitchInstance? = null
    private var pmmElementBound: HyperswitchBoundElement? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pmm_widget_activity)
        fetchPaymentMethodSession()
    }

    // ── Network ────────────────────────────────────────────────────────────────

    private fun fetchPaymentMethodSession() {
        reset().post("$serverUrl/create-payment-method-session")
            .header("Content-Type" to "application/json")
            .body(MainActivity.PAYMENT_METHOD_SESSION_BODY)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = JSONObject(value ?: return)
                        Log.d(TAG, "PM session response: $value")

                        val publishableKey   = json.getString("publishableKey")
                        val profileId        = json.getString("profileId")
                        val sdkAuthorization = json.getString("sdkAuthorization")

                        runOnUiThread {
                            initialiseWidget(publishableKey, profileId, sdkAuthorization)
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse PM session response", e)
                        setStatus("Error parsing server response")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "PM session request failed: ${error.message}")
                    setStatus("Could not connect to the server")
                }
            })
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    private fun initialiseWidget(
        publishableKey: String,
        profileId: String,
        sdkAuthorization: String,
    ) {
        hyperswitchInstance = Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
                // PM sessions are created against the new gateway (see .env
                // PM_SESSION_BASE_URL) — point every PMM API call there.
                customConfig = CustomEndpointConfiguration(
                    commonEndpoint = MainActivity.PM_SESSION_COMMON_ENDPOINT
                ),
            )
        )

        val pmmElement = findViewById<PaymentMethodsManagementElement>(R.id.pmmElement)

        lifecycleScope.launch {
            val elements = hyperswitchInstance?.elements(
                PaymentSessionConfiguration(sdkAuthorization)
            ) ?: return@launch

            pmmElementBound = elements.bind(pmmElement, buildDemoConfiguration())
            pmmElementBound?.onPaymentResult(::handleResult)

            // ── Merchant-owned confirm CTA (widget mode) ──────────────────
            // The PMM widget renders no confirm button of its own — the
            // merchant's button triggers the save via confirmTokenization()
            // (web Vault SDK parity with confirmTokenization()).
            findViewById<Button>(R.id.merchantSaveCardButton).setOnClickListener {
                pmmElementBound?.confirmTokenization(::handleResult)
                    ?: setStatus("Widget not initialised yet")
            }
        }
    }

    // ── Result handling ────────────────────────────────────────────────────────

    private fun handleResult(result: PaymentResult) {
        val message = when (result) {
            is PaymentResult.Completed -> "Completed: ${result.data}"
            is PaymentResult.Canceled  -> "Cancelled: ${result.data}"
            is PaymentResult.Failed    -> "Failed: ${result.throwable.message.orEmpty()}"
        }
        setStatus(message)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }

    // ── Constants ──────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "PaymentMethodsMgmt"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private val serverUrl: String by lazy { loadServerUrl() }
}
