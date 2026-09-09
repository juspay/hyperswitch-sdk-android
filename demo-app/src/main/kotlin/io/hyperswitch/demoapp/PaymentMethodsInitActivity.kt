package io.hyperswitch.demoapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchEnvironment
import io.hyperswitch.paymentmethods.AppearanceVariables
import io.hyperswitch.paymentmethods.BrandIconMode
import io.hyperswitch.paymentmethods.initPaymentMethodSession
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.Hyperswitch
import org.json.JSONException
import org.json.JSONObject

/**
 * Demonstrates that `initPaymentMethodSession`/`createCardForm` don't need to run in the same
 * activity that shows the card form's input fields.
 *
 * This activity does *only* the init step — it has no card field views of its own. Once the
 * [io.hyperswitch.paymentmethods.CardForm] is created, it's handed to [PaymentMethodSessionHolder]
 * and [PaymentMethodsViewActivity] is launched, which binds that same instance to its own
 * layout's widgets.
 */
class PaymentMethodsInitActivity : AppCompatActivity(), HyperInterface {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.payment_methods_init_activity)
        fetchPaymentMethodSession()
    }

    private fun fetchPaymentMethodSession() {
        setStatus("Creating payment method session…")

        reset().get("$serverUrl/create-payment-method-session")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: return
                        val publishableKey = json.getString("publishableKey")
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        val profileId = json.optString("profileId")

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

    private fun initialisePaymentMethodSession(
        publishableKey: String,
        profileId: String,
        sdkAuthorization: String,
    ) {
        val hyperswitchInstance = Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
                environment = HyperswitchEnvironment.SANDBOX,
            )
        )

        val paymentMethodSession = hyperswitchInstance.initPaymentMethodSession(sdkAuthorization)
        // Every AppearanceVariables field — session-level theming for the hyperswitch vault,
        // same as PaymentMethodsActivity's, so the split-activity flow gets identical styling.
        val variables = AppearanceVariables(
            colorPrimary = "#000000",
            colorText = "#000000",
            colorDanger = "#FF0000",
            colorTextPlaceholder = "#000000",
            colorBackground = "#FFFFFF",
            borderColor = "#000000",
            borderRadius = 0f,
            borderWidth = 3f,
            fontFamily = "sans-serif-black",
            fontScale = 1f,
            inputFieldHeight = 56f,
            gap = 12f,
            placeholderTextSizeAdjust = 0f,
            errorTextSizeAdjust = 0f,
            errorMessageSpacing = 6f,
            cardBrandIcon = BrandIconMode.STANDARD,
        )
        val cardForm = paymentMethodSession.createCardForm(buildAppearance(), variables)

        PaymentMethodSessionHolder.hyperswitchInstance = hyperswitchInstance
        PaymentMethodSessionHolder.paymentMethodSession = paymentMethodSession
        PaymentMethodSessionHolder.cardForm = cardForm

        setStatus("Card form created here — opening the view activity…")
        startActivity(Intent(this, PaymentMethodsViewActivity::class.java))
        // Deliberately not finish()ing: the CardForm's empty surface was created against this
        // activity's context, and this activity staying alive (merely paused) in the back stack
        // is the simplest way to avoid tying that surface to a finished activity.
    }

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }

    companion object {
        private const val TAG = "PaymentMethodsInitActivity"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private val serverUrl: String by lazy { loadServerUrl() }
}
