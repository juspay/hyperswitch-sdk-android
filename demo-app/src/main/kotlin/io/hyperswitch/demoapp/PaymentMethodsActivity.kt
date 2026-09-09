package io.hyperswitch.demoapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchEnvironment
import io.hyperswitch.paymentmethods.AppearanceVariables
import io.hyperswitch.paymentmethods.BrandIconMode
import io.hyperswitch.paymentmethods.CardForm
import io.hyperswitch.paymentmethods.CvcIconDisplay
import io.hyperswitch.paymentmethods.ErrorDisplay
import io.hyperswitch.paymentmethods.FieldOptions
import io.hyperswitch.paymentmethods.FieldStyles
import io.hyperswitch.paymentmethods.LabelBehavior
import io.hyperswitch.paymentmethods.PaymentMethodSession
import io.hyperswitch.paymentmethods.TextStyleProps
import io.hyperswitch.paymentmethods.TokeniseResult
import io.hyperswitch.paymentmethods.ViewStyleProps
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
        configureCardFields()
        cardForm = paymentMethodSession?.createCardForm(buildAppearance(), variables)?.also { form ->
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

    /**
     * Field-level brutal theme, set programmatically via
     * [io.hyperswitch.paymentmethods.widget.BaseRNViewInput.setOptions] instead of `app:field*`
     * XML attributes — the per-field counterpart to [initialisePaymentMethodSession]'s
     * vault/session-wide [AppearanceVariables] above. Every value here is forwarded to JS as-is;
     * this code makes no attempt to reconcile e.g. a field's box height against the label row
     * `labelBehavior = ABOVE` adds above it — that's the RN side's call, not native's.
     */
    private fun configureCardFields() {
        val black = ContextCompat.getColor(this, R.color.brutal_black)
        val white = ContextCompat.getColor(this, R.color.brutal_white)
        val yellow = ContextCompat.getColor(this, R.color.brutal_yellow)
        val red = ContextCompat.getColor(this, R.color.brutal_red)

        fun rootStyle() = ViewStyleProps(backgroundColor = yellow, padding = 0f)
        fun containerStyle(padding: Float, height: Float) = ViewStyleProps(
            backgroundColor = white,
            borderColor = black,
            borderRadius = 0f,
            borderWidth = 3f,
            padding = padding,
            height = height,
        )
        fun accessoryStyle() = ViewStyleProps(
            backgroundColor = white,
            borderRadius = 0f,
            borderWidth = 0f,
            padding = 2f,
        )
        fun textStyle(fontSize: Float, color: Int = black) = TextStyleProps(color = color, fontSize = fontSize)

        findViewById<CardHolderInputField>(R.id.cardHolderInput).setOptions(
            styles = FieldStyles(
                root = rootStyle(),
                container = containerStyle(padding = 14f, height = 56f),
                input = textStyle(17f),
                placeholder = textStyle(17f),
                label = textStyle(12f),
                error = textStyle(12f, red),
            ),
            options = FieldOptions(
                label = "Cardholder name",
                labelBehavior = LabelBehavior.ABOVE,
                errorDisplay = ErrorDisplay.COLOR_ONLY,
                unstyled = false,
                accessibilityLabel = "Cardholder name input",
                accessibilityHint = "Enter the name printed on the card",
            ),
            placeholder = "Cardholder name",
        )

        findViewById<CardNumberInputField>(R.id.cardNumberInput).setOptions(
            styles = FieldStyles(
                root = rootStyle(),
                container = containerStyle(padding = 14f, height = 56f),
                input = textStyle(17f),
                placeholder = textStyle(17f),
                label = textStyle(12f),
                error = textStyle(12f, red),
                accessory = accessoryStyle(),
            ),
            options = FieldOptions(
                label = "Card number",
                labelBehavior = LabelBehavior.FLOATING,
                errorDisplay = ErrorDisplay.INLINE,
                unstyled = false,
                accessibilityLabel = "Card number input",
                accessibilityHint = "Enter your 16 digit card number",
                cardBrandIcon = BrandIconMode.STANDARD,
            ),
            placeholder = "Card number",
        )

        findViewById<CardExpiryInputField>(R.id.cardExpiryInput).setOptions(
            styles = FieldStyles(
                root = rootStyle(),
                container = containerStyle(padding = 12f, height = 52f),
                input = textStyle(15f),
                placeholder = textStyle(15f),
                label = textStyle(11f),
                error = textStyle(11f, red),
            ),
            options = FieldOptions(
                label = "Expiry",
                labelBehavior = LabelBehavior.NEVER,
                errorDisplay = ErrorDisplay.NONE,
                unstyled = false,
                accessibilityLabel = "Expiry date input",
                accessibilityHint = "Enter the card expiry date",
            ),
            placeholder = "MM / YY",
        )

        findViewById<CardCVCInputField>(R.id.cardCVCInput).setOptions(
            styles = FieldStyles(
                root = rootStyle(),
                container = containerStyle(padding = 12f, height = 52f),
                input = textStyle(15f),
                placeholder = textStyle(15f),
                label = textStyle(11f),
                error = textStyle(11f, red),
                accessory = accessoryStyle(),
            ),
            options = FieldOptions(
                label = "CVC",
                labelBehavior = LabelBehavior.NEVER,
                errorDisplay = ErrorDisplay.NONE,
                unstyled = false,
                accessibilityLabel = "CVC input",
                accessibilityHint = "Enter the 3 digit security code",
                cvcIcon = CvcIconDisplay.DEFAULT,
            ),
            placeholder = "CVC",
        )
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
