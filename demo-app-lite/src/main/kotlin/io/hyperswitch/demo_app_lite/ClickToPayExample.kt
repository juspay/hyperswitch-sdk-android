package io.hyperswitch.demo_app_lite

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.click_to_pay.ClickToPaySession
import io.hyperswitch.click_to_pay.models.*
import kotlinx.coroutines.launch
import org.json.JSONObject

class ClickToPayExample : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnStart: Button
    private lateinit var signOut: Button
    private var recognizedCards: List<RecognizedCard>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)

        resultText = findViewById(R.id.resultText)
        btnStart = findViewById(R.id.btnStep1)
        signOut = findViewById(R.id.signOut)
        signOut.visibility = INVISIBLE

        btnStart.text = "Start Click to Pay Flow"
        btnStart.setOnClickListener { startClickToPayFlow() }

        updateResultText("Click 'Start Click to Pay Flow' to begin")

        val rotatingView = findViewById<ImageView>(R.id.rotatingView)
        val rotatingView2 = findViewById<ImageView>(R.id.rotatingView2)

        rotatingView.animate()
            .rotationBy(360f)
            .setDuration(1000L)
            .setInterpolator(LinearInterpolator()) // uniform speed
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Repeat infinitely without any restart lag
                    rotatingView.animate().rotationBy(360f)
                        .setDuration(1000L)
                        .setInterpolator(LinearInterpolator())
                        .setListener(this)
                        .start()
                }
            })
            .start()

        rotatingView2.animate()
            .rotationBy(-360f)
            .setDuration(1000L)
            .setInterpolator(LinearInterpolator()) // uniform speed
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Repeat infinitely without any restart lag
                    rotatingView2.animate().rotationBy(-360f)
                        .setDuration(1000L)
                        .setInterpolator(LinearInterpolator())
                        .setListener(this)
                        .start()
                }
            })
            .start()
    }

    private fun startClickToPayFlow() {
        btnStart.isEnabled = false
        updateResultText("Initializing Click to Pay...")
        getAuthenticationCredentials()
    }

    private fun getAuthenticationCredentials() {
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
                            initClickToPay(credentials)
                        } else {
                            updateResultText("Failed: Create Authentication Result is null")
                        }
                    } catch (e: ClickToPayException) {
                        updateResultText("Failed: ${e.message}")
                    }
                }

                override fun failure(error: FuelError) {
                    updateResultText("Failed: ${error.message}")
                }
            })
    }

    private fun initClickToPay(credentials: Credentials) {
        lifecycleScope.launch {
            // Step 2 & 3: Initialize sessions (now non-blocking)
            try {
                val authSession =
                    AuthenticationSession(this@ClickToPayExample, credentials.publishableKey)
                authSession.initAuthenticationSession(
                    credentials.clientSecret,
                    credentials.profileId,
                    credentials.authenticationId,
                    credentials.merchantId
                )
                val clickToPaySession = authSession.initClickToPaySession()
                updateResultText("✓ Sessions initialized\nChecking customer...")
                signOut.visibility = VISIBLE
                signOut.setOnClickListener {
                    signOut(clickToPaySession)
                }

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
            } catch(e: Exception){
                e.printStackTrace()
            }
        }
    }

    private fun showOTPDialog(session: ClickToPaySession) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter OTP"
        }
        AlertDialog.Builder(this)
            .setTitle("Verify Your Identity")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                lifecycleScope.launch {
                    try {
                        updateResultText("Verifying OTP...")
                        recognizedCards =
                            session.validateCustomerAuthentication(input.text.toString())
                        showCardSelection(session)
                    } catch (e: ClickToPayException) {
                        showError("Invalid OTP: ${e.message}")
                    }
                }
            }
            .show()
    }

    private fun showCardSelection(session: ClickToPaySession, errorMessage : String? = "") {
        val cards = recognizedCards ?: return
        updateResultText("✓ Found ${cards.size} card(s)\nSelect a card to checkout")

        AlertDialog.Builder(this)
            .setTitle(errorMessage ?: "Select Card")
            .setItems(cards.map { "**** ${it.panLastFour}" }.toTypedArray()) { _, which ->
                checkout(session, cards[which])
            }
            .show()
    }

    private fun checkout(
        session: io.hyperswitch.click_to_pay.ClickToPaySession,
        card: RecognizedCard
    ) {
        lifecycleScope.launch {
            try {
                updateResultText("Processing payment...")
                val response =
                    session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))

                if (response?.status == AuthenticationStatus.SUCCESS) {

                    when (val vault = response.vaultTokenData) {
                        is PaymentData.CardData -> {
                            println("Card number: ${vault.cardNumber}")
                        }
                        is PaymentData.NetworkTokenData -> {
                            println("Network token: ${vault.networkToken}")
                        }
                        null -> {
                            println("No vault token data")
                        }
                    }

                    updateResultText(
                        "✓ Payment Successful!\n\nCard: **** ${card.panLastFour}\n" +
                                "Amount: ${response.amount} ${response.currency}\nStatus: ${response.transStatus}"
                    )
                    Toast.makeText(this@ClickToPayExample, "Payment completed!", Toast.LENGTH_SHORT)
                        .show()

                } else {
                    showError("Payment failed")
                    signOut.visibility = INVISIBLE
                }
            } catch (e: ClickToPayException) {
                if (e.type == ClickToPayErrorType.CHANGE_CARD){
                    Toast.makeText(this@ClickToPayExample,"You should not change card", Toast.LENGTH_LONG ).show()
                    showCardSelection(session, "You cannot change card, Select card")
                } else if (e.type == ClickToPayErrorType.SWITCH_CONSUMER){
                    Toast.makeText(this@ClickToPayExample,"You should not change user", Toast.LENGTH_LONG ).show()
                    showCardSelection(session, "You cannot change user, select card")
                } else {
                    showError("Checkout error: ${e.reason}")
                    signOut.visibility = INVISIBLE
                }
            } finally {
                btnStart.isEnabled = true
            }
        }
    }
    private fun signOut(session: ClickToPaySession){
        lifecycleScope.launch {
            try {
                val response = session.signOut()
                if (response.recognized == false) {
                    signOut.visibility = INVISIBLE
                    updateResultText(response.toString())
                }
            } catch (e: Exception){
                showError("Cannot signout")
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
