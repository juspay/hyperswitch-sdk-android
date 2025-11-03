package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.click_to_pay.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ClickToPayExample : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnStart: Button
    private var recognizedCards: List<RecognizedCard>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)

        resultText = findViewById(R.id.resultText)
        btnStart = findViewById(R.id.btnStep1)

        btnStart.text = "Start Click to Pay Flow"
        btnStart.setOnClickListener { startClickToPayFlow() }

        // Hide other buttons
        findViewById<Button>(R.id.btnStep2).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnStep3).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnStep4).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnStep5).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnStep6).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnScanCard).visibility = android.view.View.GONE

        updateResultText("Click 'Start Click to Pay Flow' to begin")
    }

    private fun startClickToPayFlow() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                btnStart.isEnabled = false
                updateResultText("Initializing Click to Pay...")

                // Step 1: Get credentials from API
                val credentials = getAuthenticationCredentials()
                updateResultText("✓ Got authentication credentials\nInitializing session...")

                // Step 2 & 3: Initialize sessions
                val authSession = AuthenticationSession(this@ClickToPayExample, credentials.publishableKey)
                authSession.initAuthenticationSession(
                    credentials.clientSecret,
                    credentials.profileId,
                    credentials.authenticationId,
                    credentials.merchantId
                )
                val clickToPaySession = authSession.initClickToPaySession()
                updateResultText("✓ Sessions initialized\nChecking customer...")

                // Step 4: Check customer presence
                val customerPresent = clickToPaySession.isCustomerPresent(CustomerPresenceRequest())
                if (customerPresent?.customerPresent != true) {
                    showError("Customer not found in Click to Pay")
                    return@launch
                }
                updateResultText("✓ Customer found\nRetrieving cards...")

                // Step 5: Get cards
                val cardsStatus = clickToPaySession.getUserType()
                when (cardsStatus?.statusCode) {
                    StatusCode.RECOGNIZED_CARDS_PRESENT -> {
                        recognizedCards = clickToPaySession.getRecognizedCards()
                        showCardSelection(clickToPaySession)
                    }
                    StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION -> {
                        showOTPDialog(clickToPaySession)
                    }
                    else -> {
                        showError("No cards found")
                    }
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
                btnStart.isEnabled = true
            }
        }
    }

    private suspend fun getAuthenticationCredentials(): Credentials {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    Fuel.post("http://10.0.2.2:5252/create-authentication")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .body("{}")
                        .responseString(object : Handler<String?> {
                            override fun success(value: String?) {
                                try {
                                    val result = value?.let { JSONObject(it) }
                                    if (result != null) {
                                        val credentials = Credentials(
                                            publishableKey = result.getString("publishableKey"),
                                            clientSecret = result.getString("clientSecret"),
                                            profileId = result.getString("profileId"),
                                            authenticationId = result.getString("authenticationId"),
                                            merchantId = result.getString("merchantId"),
                                        )

                                        continuation.resumeWith(Result.success(credentials))
                                    } else {
                                        continuation.resumeWith(Result.failure(Throwable("Invalid response from server")))
                                    }
                                } catch (e: Exception) {
                                    continuation.resumeWith(Result.failure(e))
                                }
                            }

                            override fun failure(error: FuelError) {
                                continuation.resumeWith(Result.failure(Throwable("Failed to get authentication credentials: ${error.message}")))
                            }
                        })
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }

    private fun showOTPDialog(session: io.hyperswitch.click_to_pay.ClickToPaySession) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter OTP"
        }
        AlertDialog.Builder(this)
            .setTitle("Verify Your Identity")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        recognizedCards = session.validateCustomerAuthentication(input.text.toString())
                        showCardSelection(session)
                    } catch (e: Exception) {
                        showError("Invalid OTP: ${e.message}")
                    }
                }
            }
            .show()
    }

    private fun showCardSelection(session: io.hyperswitch.click_to_pay.ClickToPaySession) {
        val cards = recognizedCards ?: return
        updateResultText("✓ Found ${cards.size} card(s)\nSelect a card to checkout")

        AlertDialog.Builder(this)
            .setTitle("Select Card")
            .setItems(cards.map { "**** ${it.panLastFour}" }.toTypedArray()) { _, which ->
                checkout(session, cards[which])
            }
            .show()
    }

    private fun checkout(session: io.hyperswitch.click_to_pay.ClickToPaySession, card: RecognizedCard) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                updateResultText("Processing payment...")
                val response = session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))

                if (response?.status == "success") {
                    updateResultText("✓ Payment Successful!\n\nCard: **** ${card.panLastFour}\n" +
                        "Amount: ${response.amount} ${response.currency}\nStatus: ${response.transStatus}")
                    Toast.makeText(this@ClickToPayExample, "Payment completed!", Toast.LENGTH_SHORT).show()
                } else {
                    showError("Payment failed")
                }
                btnStart.isEnabled = true
            } catch (e: Exception) {
                showError("Checkout error: ${e.message}")
                btnStart.isEnabled = true
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateResultText("Error: $message")
    }

    private fun updateResultText(text: String) {
        resultText.text = text
    }

    data class Credentials(
        val publishableKey: String,
        val clientSecret: String,
        val profileId: String,
        val authenticationId: String,
        val merchantId: String
    )
}
