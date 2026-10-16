package io.hyperswitch.demoapp

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentmethods.CardFieldOptions
import io.hyperswitch.paymentmethods.CardForm
import io.hyperswitch.paymentmethods.PaymentMethodSessionConfiguration
import io.hyperswitch.paymentmethods.TokenizeResult
import io.hyperswitch.paymentmethods.initPaymentMethodSession
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.sdk.Hyperswitch
import org.json.JSONObject

/**
 * Payment Methods SDK demo: three card fields placed by this screen, among its own views,
 * acting as one form. It saves the card on a payment method session, or, started with
 * [EXTRA_PAYS], pays a payment with it.
 */
class PaymentMethodsActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var tokenize: Button

    private var cardForm: CardForm? = null

    private val pays: Boolean by lazy { intent.getBooleanExtra(EXTRA_PAYS, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        status = caption(if (pays) "Creating a payment…" else "Creating a payment method session…")
        tokenize = Button(this).apply {
            text = if (pays) "Pay" else "Tokenize"
            isEnabled = false
            setOnClickListener { if (pays) pay() else tokenize() }
        }
        content.addView(status)
        setContentView(ScrollView(this).apply { addView(content) })

        if (pays) createPayment() else createSession()
    }

    private fun createSession() {
        reset().post("${serverUrl()}/create-payment-method-session")
            .header("Content-Type" to "application/json")
            .body("{}")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = JSONObject(value ?: "{}")
                        val publishableKey = json.getString("publishableKey")
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        runOnUiThread {
                            show(
                                Hyperswitch.init(this@PaymentMethodsActivity, HyperswitchConfiguration(publishableKey = publishableKey))
                                    .initPaymentMethodSession(PaymentMethodSessionConfiguration(sdkAuthorization))
                                    .createCardForm()
                            )
                        }
                    } catch (e: Exception) {
                        runOnUiThread { status.text = "The server did not return a session: $value" }
                    }
                }

                override fun failure(error: FuelError) {
                    runOnUiThread { status.text = "Could not reach the demo server: ${error.message}" }
                }
            })
    }

    /** The same form and fields, made from a payment's elements: they pay instead of saving. */
    private fun createPayment() {
        reset().get("${serverUrl()}/create-payment-intent")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = JSONObject(value ?: "{}")
                        val configuration = HyperswitchConfiguration(
                            publishableKey = json.getString("publishableKey"),
                            profileId = json.optString("profileId"),
                        )
                        val session = PaymentSessionConfiguration(json.getString("sdkAuthorization"))
                        runOnUiThread {
                            Hyperswitch.init(this@PaymentMethodsActivity, configuration)
                                .elements(session) { elements -> show(elements.createCardForm()) }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { status.text = "The server did not return a payment: $value" }
                    }
                }

                override fun failure(error: FuelError) {
                    runOnUiThread { status.text = "Could not reach the demo server: ${error.message}" }
                }
            })
    }

    private fun show(form: CardForm) {
        cardForm = form

        val number = form.cardNumberField(CardFieldOptions(placeholder = "Card number"))
        val expiry = form.cardExpiryField()
        val cvc = form.cardCvcField()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(expiry, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            addView(cvc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        }

        /* The labels are this screen's own views, between the SDK's fields. */
        content.removeAllViews()
        listOf(
            caption("Card details").apply { textSize = 18f; setTextColor(Color.BLACK) },
            number,
            caption("Your card is stored securely. This line belongs to the app, not the SDK."),
            row,
            tokenize,
            status,
        ).forEach {
            content.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        }

        form.onReady = { status.text = "Form ready." }
        form.onError = { status.text = "Form error: ${it.message}" }
        form.onChange = { state -> tokenize.isEnabled = state.isComplete && state.isValid }
    }

    private fun tokenize() {
        val form = cardForm ?: return
        tokenize.isEnabled = false
        status.text = "Tokenizing…"
        form.tokenize { result ->
            when (result) {
                is TokenizeResult.Success ->
                    status.text = "Token: ${result.card.paymentMethodToken ?: result.card.tokens}"

                is TokenizeResult.Failure -> {
                    status.text = "Failed (${result.error.code}): ${result.error.message}"
                    tokenize.isEnabled = true
                }
            }
        }
    }

    private fun pay() {
        val form = cardForm ?: return
        tokenize.isEnabled = false
        status.text = "Paying…"
        form.confirmPayment { result ->
            when (result) {
                is PaymentResult.Completed -> status.text = "Payment: ${result.data}"
                is PaymentResult.Canceled -> {
                    status.text = "Payment cancelled: ${result.data}"
                    tokenize.isEnabled = true
                }

                is PaymentResult.Failed -> {
                    status.text = "Failed (${result.throwable.cause?.message}): ${result.throwable.message}"
                    tokenize.isEnabled = true
                }
            }
        }
    }

    private fun serverUrl() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.GRAY)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Pay a payment with the form instead of saving a card. */
        const val EXTRA_PAYS = "pays"

        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }
}
