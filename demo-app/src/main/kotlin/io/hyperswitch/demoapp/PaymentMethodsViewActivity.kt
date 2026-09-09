package io.hyperswitch.demoapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.paymentmethods.TokeniseResult
import io.hyperswitch.paymentmethods.widget.CardCVCInputField
import io.hyperswitch.paymentmethods.widget.CardExpiryInputField
import io.hyperswitch.paymentmethods.widget.CardHolderInputField
import io.hyperswitch.paymentmethods.widget.CardNumberInputField

/**
 * Shows the card-form input field views for the [io.hyperswitch.paymentmethods.CardForm]
 * created by [PaymentMethodsInitActivity], via [PaymentMethodSessionHolder].
 *
 * Proves `createCardForm()` and the field views it's bound to don't need to live in the same
 * activity — only that the process (and therefore the holder) stays alive in between.
 */
class PaymentMethodsViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.payment_methods_view_activity)

        val cardForm = PaymentMethodSessionHolder.cardForm
        if (cardForm == null) {
            setStatus("No card form found — launch this screen via \"Payment Methods (Split Activities)\".")
            return
        }

        cardForm.bind(
            listOf(
                findViewById<CardNumberInputField>(R.id.cardNumberInput),
                findViewById<CardHolderInputField>(R.id.cardHolderInput),
                findViewById<CardExpiryInputField>(R.id.cardExpiryInput),
                findViewById<CardCVCInputField>(R.id.cardCVCInput),
            )
        )
        setStatus("Card form ready — created in a different activity")

        findViewById<View>(R.id.tokeniseButton).setOnClickListener {
            setStatus("Tokenising…")
            cardForm.tokenise { result ->
                when (result) {
                    is TokeniseResult.Success ->
                        setStatus("Tokenise success: ${result.token}")
                    is TokeniseResult.Failure ->
                        setStatus("Tokenise failed: ${result.error.code} — ${result.error.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        PaymentMethodSessionHolder.clear()
        super.onDestroy()
    }

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }
}
