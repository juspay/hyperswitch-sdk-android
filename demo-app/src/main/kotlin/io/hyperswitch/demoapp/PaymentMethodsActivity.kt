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
            colorPrimary = "#0E7AFE",
            colorText = "#1A1A1A",
            colorDanger = "#D32F2F",
            colorTextPlaceholder = "#888888",
            colorBackground = "#FFFFFF",
            borderColor = "#E2E8F0",
            borderRadius = 12f,
            borderWidth = 1f,
            fontScale = 1f,
            inputFieldHeight = 52f,
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
     * Field-level styling, set programmatically via
     * [io.hyperswitch.paymentmethods.widget.BaseRNViewInput.setOptions] instead of `app:field*`
     * XML attributes — the per-field counterpart to [initialisePaymentMethodSession]'s
     * vault/session-wide [AppearanceVariables] above. Every value here is forwarded to JS as-is;
     * this code makes no attempt to reconcile e.g. a field's box height against anything a
     * label row might add above it — that's the RN side's call, not native's.
     */
    private fun configureCardFields() {
        val text = ContextCompat.getColor(this, R.color.text_primary)
        val placeholderText = ContextCompat.getColor(this, R.color.text_caption)
        val labelText = ContextCompat.getColor(this, R.color.text_secondary)
        val surface = ContextCompat.getColor(this, R.color.surface_card)
        val border = ContextCompat.getColor(this, R.color.divider)
        val error = ContextCompat.getColor(this, R.color.color_error)

        // No `padding` here: ViewStyleProps only has one padding value (all four sides), and the
        // vault SDK sizes this box to a fixed `height` while centering its floating label inside
        // that budget — a uniform padding eats into the vertical room the label needs once it
        // animates to its focused/compact state and clips. The SDK's own default horizontal-only
        // inset already looks right, so leave vertical spacing to `height` alone.
        fun containerStyle() = ViewStyleProps(
            backgroundColor = surface,
            borderColor = border,
            borderRadius = 12f,
            borderWidth = 1f,
        )
        fun accessoryStyle() = ViewStyleProps(backgroundColor = surface, padding = 4f)
        fun textStyle(fontSize: Float, color: Int = text) = TextStyleProps(color = color, fontSize = fontSize)

        findViewById<CardHolderInputField>(R.id.cardHolderInput).setOptions(
            styles = FieldStyles(
                container = containerStyle(),
                input = textStyle(15f),
                placeholder = textStyle(15f, placeholderText),
                label = textStyle(12f, labelText),
                error = textStyle(12f, error),
            ),
            options = FieldOptions(
                label = "Cardholder name",
                labelBehavior = LabelBehavior.FLOATING,
                errorDisplay = ErrorDisplay.INLINE,
                unstyled = false,
                accessibilityLabel = "Cardholder name input",
                accessibilityHint = "Enter the name printed on the card",
            ),
            placeholder = "Cardholder name",
        )

        findViewById<CardNumberInputField>(R.id.cardNumberInput).setOptions(
            styles = FieldStyles(
                container = containerStyle(),
                input = textStyle(15f),
                placeholder = textStyle(15f, placeholderText),
                label = textStyle(12f, labelText),
                error = textStyle(12f, error),
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
                container = containerStyle(),
                input = textStyle(15f),
                placeholder = textStyle(15f, placeholderText),
                label = textStyle(11f, labelText),
                error = textStyle(11f, error),
            ),
            options = FieldOptions(
                label = "Expiry",
                labelBehavior = LabelBehavior.FLOATING,
                errorDisplay = ErrorDisplay.INLINE,
                unstyled = false,
                accessibilityLabel = "Expiry date input",
                accessibilityHint = "Enter the card expiry date",
            ),
            placeholder = "MM / YY",
        )

        findViewById<CardCVCInputField>(R.id.cardCVCInput).setOptions(
            styles = FieldStyles(
                container = containerStyle(),
                input = textStyle(15f),
                placeholder = textStyle(15f, placeholderText),
                label = textStyle(11f, labelText),
                error = textStyle(11f, error),
                accessory = accessoryStyle(),
            ),
            options = FieldOptions(
                label = "CVC",
                labelBehavior = LabelBehavior.FLOATING,
                errorDisplay = ErrorDisplay.INLINE,
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
