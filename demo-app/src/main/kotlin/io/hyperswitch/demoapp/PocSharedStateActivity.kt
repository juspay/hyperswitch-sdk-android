package io.hyperswitch.demoapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.sdk.Elements
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchBoundElement
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.view.PaymentElement
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * POC ONLY — DELETE AFTER DEMO.
 *
 * Mounts TWO PaymentElement views bound to TWO separate Elements sessions.
 * Each widget gets its own ReactRootView (own JS props/rootTag), but both live
 * on the same RN VM. The JS-side PocSharedState module-level Dict is written
 * from one root and read from the other, proving module scope is per-VM — the
 * reason PrefetchCache must be keyed by sdkAuthorization.
 */
class PocSharedStateActivity : AppCompatActivity() {

    private var hyperswitchInstance: HyperswitchInstance? = null
    private var elementsA: Elements? = null
    private var elementsB: Elements? = null
    private var boundA: HyperswitchBoundElement? = null
    private var boundB: HyperswitchBoundElement? = null
    private var paymentSessionHandler: PaymentSessionHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poc_shared_state_activity)
        fetchPaymentIntent()

        findViewById<View>(R.id.pocTriggerHeadless).setOnClickListener {
            // Runs a HyperHeadless task on the SAME React context as the widgets.
            // JS-side [SHAREDPoC] HEADLESS VM CHECK will print whether the task
            // sees the widget roots' module writes -> same VM vs separate VM proof.
            setStatus("Launching headless…")
            lifecycleScope.launch {
                paymentSessionHandler = elementsA?.getPaymentSession()?.getCustomerSavedPaymentMethods()
                Log.i(TAG, "headless handler returned: $paymentSessionHandler")
                setStatus("Headless ran — check logcat for [SHAREDPoC] HEADLESS VM CHECK")
            }
        }
    }

    private fun fetchPaymentIntent() {
        setStatus("Fetching payment intent…")
        reset().get("$serverUrl/create-payment-intent")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = JSONObject(value ?: return)
                        val publishableKey = json.getString("publishableKey")
                        val profileId = json.optString("profileId")
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        runOnUiThread { initialiseWidgets(publishableKey, profileId, sdkAuthorization) }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse server response", e)
                        setStatus("Error parsing server response")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "Backend request failed: ${error.message}")
                    setStatus("Mock server unreachable — run `yarn server`")
                }
            })
    }

    private fun initialiseWidgets(publishableKey: String, profileId: String, sdkAuthorization: String) {
        hyperswitchInstance = Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
            )
        )

        val sessionConfig = PaymentSessionConfiguration(sdkAuthorization)
        val paymentElementA = findViewById<PaymentElement>(R.id.pocPaymentElementA)
        val paymentElementB = findViewById<PaymentElement>(R.id.pocPaymentElementB)

        lifecycleScope.launch {
            try {
                // Two independent Elements sessions -> two separate React roots on one VM.
                elementsA = hyperswitchInstance?.elements(sessionConfig)
                elementsB = hyperswitchInstance?.elements(sessionConfig)

                boundA = elementsA?.bind(paymentElementA, buildDemoConfiguration())
                boundB = elementsB?.bind(paymentElementB, buildDemoConfiguration())
                setStatus("Widgets ready. Tap WRITE in widget B, then READ SHARED in widget A.")
            } catch (error: Exception) {
                Log.e(TAG, "Widget initialisation failed", error)
                setStatus("Widget init failed: ${error.message.orEmpty()}")
            }
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.pocStatus).text = message }
    }

    companion object {
        private const val TAG = "PocSharedState"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private val serverUrl: String by lazy { loadServerUrl() }
}
