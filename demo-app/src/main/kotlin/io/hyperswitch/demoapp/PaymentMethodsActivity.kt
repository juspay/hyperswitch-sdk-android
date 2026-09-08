package io.hyperswitch.demoapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchEnvironment
import io.hyperswitch.paymentmethods.CardForm
import io.hyperswitch.paymentmethods.PaymentMethodSession
import io.hyperswitch.paymentmethods.TokeniseResult
import io.hyperswitch.paymentmethods.initPaymentMethodSession
import io.hyperswitch.paymentmethods.widget.CardCVCInputField
import io.hyperswitch.paymentmethods.widget.CardExpiryInputField
import io.hyperswitch.paymentmethods.widget.CardHolderInputField
import io.hyperswitch.paymentmethods.widget.CardNumberInputField
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchInstance
import org.json.JSONException
import org.json.JSONObject

class PaymentMethodsActivity : AppCompatActivity(), HyperInterface {

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    private var hyperswitchInstance: HyperswitchInstance? = null
    private var paymentMethodSession: PaymentMethodSession? = null
    private var cardForm: CardForm? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.payment_methods_activity)

        findViewById<View>(R.id.reloadInstanceButton).setOnClickListener { reloadInstance() }
        findViewById<View>(R.id.tokeniseButton).setOnClickListener {
            setStatus("Tokenising…")
            cardForm?.tokenise { result ->
                when (result) {
                    is TokeniseResult.Success -> setStatus("Tokenise success: ${result.token}")
                    is TokeniseResult.Failure -> setStatus("Tokenise failed: ${result.error.code} — ${result.error.message}")
                }
            }
        }

        fetchPaymentMethodSession()
    }

    override fun onDestroy() {
        cardForm?.release()
        paymentMethodSession?.release()
        super.onDestroy()
    }

    // ── Reload ─────────────────────────────────────────────────────────────────────────────────

    private fun reloadInstance() {
        cardForm?.release()
        paymentMethodSession?.release()
        cardForm = null
        paymentMethodSession = null

        fetchPaymentMethodSession()
    }

    // ── Network ────────────────────────────────────────────────────────────────────────────────
    private fun fetchPaymentMethodSession() {
        setStatus("Creating payment method session…")

        reset().get("$serverUrl/create-payment-method-session")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: return
                        val publishableKey  = json.getString("publishableKey")
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        val profileId       = json.optString("profileId")

                        runOnUiThread { initialisePaymentMethodSession(publishableKey, profileId, sdkAuthorization) }
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
    }

    // ── Initialisation ─────────────────────────────────────────────────────────────────────────
    private fun initialisePaymentMethodSession(
        publishableKey: String,
        profileId: String,
        sdkAuthorization: String,
    ) {
        hyperswitchInstance = Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
                environment = HyperswitchEnvironment.SANDBOX,
            )
        )

        paymentMethodSession = hyperswitchInstance?.initPaymentMethodSession(sdkAuthorization)
        cardForm = paymentMethodSession?.createCardForm(buildAppearance())?.also { form ->
            form.bind(
                listOf(
                    findViewById<CardNumberInputField>(R.id.cardNumberInput),
                    findViewById<CardHolderInputField>(R.id.cardHolderInput),
                    findViewById<CardExpiryInputField>(R.id.cardExpiryInput),
                    findViewById<CardCVCInputField>(R.id.cardCVCInput),
                )
            )
        }

        setStatus("Card form ready")
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────────────────────

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }

    // ── Constants ──────────────────────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "PaymentMethodsActivity"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private val serverUrl: String by lazy { loadServerUrl() }
}
