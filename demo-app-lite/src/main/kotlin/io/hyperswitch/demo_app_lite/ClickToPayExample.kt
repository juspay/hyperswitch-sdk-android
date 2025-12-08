package io.hyperswitch.demo_app_lite

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View.GONE
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

    // UI Elements
    private lateinit var resultText: TextView
    private lateinit var btnGetCredentials: Button
    private lateinit var btnInitAuth: Button
    private lateinit var btnInitC2P: Button
    private lateinit var btnCheckCustomer: Button
    private lateinit var btnGetUserType: Button
    private lateinit var btnValidateOTP: Button
    private lateinit var btnGetCards: Button
    private lateinit var btnSelectCard: Button
    private lateinit var btnCheckout: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnReset: Button

    // State Variables
    private var credentials: Credentials? = null
    private var authSession: AuthenticationSession? = null
    private var clickToPaySession: ClickToPaySession? = null
    private var recognizedCards: List<RecognizedCard>? = null
    private var selectedCard: RecognizedCard? = null
    private var needsOTPValidation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)

        initializeViews()
        setupClickListeners()
        setupAnimations()
        updateResultText("Ready to start\n\nClick 'Get Authentication Credentials' to begin the Click to Pay flow.")
    }

    private fun initializeViews() {
        resultText = findViewById(R.id.resultText)
        btnGetCredentials = findViewById(R.id.btnGetCredentials)
        btnInitAuth = findViewById(R.id.btnInitAuth)
        btnInitC2P = findViewById(R.id.btnInitC2P)
        btnCheckCustomer = findViewById(R.id.btnCheckCustomer)
        btnGetUserType = findViewById(R.id.btnGetUserType)
        btnValidateOTP = findViewById(R.id.btnValidateOTP)
        btnGetCards = findViewById(R.id.btnGetCards)
        btnSelectCard = findViewById(R.id.btnSelectCard)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnSignOut = findViewById(R.id.btnSignOut)
        btnReset = findViewById(R.id.btnReset)
    }

    private fun setupClickListeners() {
        btnGetCredentials.setOnClickListener { handleGetCredentials() }
        btnInitAuth.setOnClickListener { handleInitAuth() }
        btnInitC2P.setOnClickListener { handleInitC2P() }
        btnCheckCustomer.setOnClickListener { handleCheckCustomer() }
        btnGetUserType.setOnClickListener { handleGetUserType() }
        btnValidateOTP.setOnClickListener { handleValidateOTP() }
        btnGetCards.setOnClickListener { handleGetCards() }
        btnSelectCard.setOnClickListener { handleSelectCard() }
        btnCheckout.setOnClickListener { handleCheckout() }
        btnSignOut.setOnClickListener { handleSignOut() }
        btnReset.setOnClickListener { handleReset() }
    }

    private fun setupAnimations() {
        val rotatingView = findViewById<ImageView>(R.id.rotatingView)
        val rotatingView2 = findViewById<ImageView>(R.id.rotatingView2)

        rotatingView.animate()
            .rotationBy(360f)
            .setDuration(1000L)
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
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
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rotatingView2.animate().rotationBy(-360f)
                        .setDuration(1000L)
                        .setInterpolator(LinearInterpolator())
                        .setListener(this)
                        .start()
                }
            })
            .start()
    }

    // ============================================================================
    // API Method Handlers
    // ============================================================================

    private fun handleGetCredentials() {
        updateResultText("Fetching authentication credentials from server...\n\nEndpoint: /create-authentication")

        Fuel.post("http://10.0.2.2:5252/create-authentication")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .body("{}")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    runOnUiThread {
                        try {
                            val result = value?.let { JSONObject(it) }
                            if (result != null) {
                                credentials = Credentials(
                                    publishableKey = result.getString("publishableKey"),
                                    clientSecret = result.getString("clientSecret"),
                                    profileId = result.getString("profileId"),
                                    authenticationId = result.getString("authenticationId"),
                                    merchantId = result.getString("merchantId"),
                                )
                                updateResultText(
                                    "Credentials received successfully!\n\n" +
                                            "publishableKey: ${credentials?.publishableKey?.take(20)}...\n" +
                                            "clientSecret: ${credentials?.clientSecret?.take(20)}...\n" +
                                            "profileId: ${credentials?.profileId}\n" +
                                            "authenticationId: ${credentials?.authenticationId}\n" +
                                            "merchantId: ${credentials?.merchantId}\n\n" +
                                            "Next: Click 'Initialize Authentication Session'"
                                )
                                btnInitAuth.isEnabled = true
                            } else {
                                showError("Failed: Create Authentication Result is null")
                            }
                        } catch (e: Exception) {
                            showError("Failed to parse credentials: ${e.message}")
                        }
                    }
                }

                override fun failure(error: FuelError) {
                    runOnUiThread {
                        showError("Network error: ${error.message}")
                    }
                }
            })
    }

    private fun handleInitAuth() {
        val creds = credentials ?: run {
            showError("Credentials not found")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText("Initializing Authentication Session...\n\nCreating AuthenticationSession instance...")
                authSession = AuthenticationSession(this@ClickToPayExample, creds.publishableKey)
                authSession?.initAuthenticationSession(
                    creds.clientSecret,
                    creds.profileId,
                    creds.authenticationId,
                    creds.merchantId
                )
                updateResultText(
                    "Authentication Session initialized!\n\n" +
                            "Session is ready for Click to Pay initialization.\n\n" +
                            "Next: Click 'Initialize Click to Pay Session'"
                )
                btnInitC2P.isEnabled = true
            } catch (e: Exception) {
                showError("Auth init failed: ${e.message}")
            }
        }
    }

    private fun handleInitC2P() {
        val session = authSession ?: run {
            showError("Authentication session not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText("Initializing Click to Pay Session...\n\nConnecting to Click to Pay network...")
                clickToPaySession = session.initClickToPaySession()
                updateResultText(
                    "Click to Pay Session initialized!\n\n" +
                            "Ready to check customer presence.\n\n" +
                            "Next: Click 'Check Customer Presence'"
                )
                btnCheckCustomer.isEnabled = true
                btnSignOut.isEnabled = true
            } catch (e: Exception) {
                showError("C2P init failed: ${e.message}")
            }
        }
    }

    private fun handleCheckCustomer() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText("Checking customer presence...\n\nQuerying Click to Pay network...")
                val customerPresent = session.isCustomerPresent(CustomerPresenceRequest())
                if (customerPresent?.customerPresent == true) {
                    updateResultText(
                        "Customer found in Click to Pay network!\n\n" +
                                "Customer is enrolled and has cards available.\n\n" +
                                "Next: Click 'Get User Type & Card Status'"
                    )
                    btnGetUserType.isEnabled = true
                } else {
                    showError("Customer not found in Click to Pay network")
                }
            } catch (e: Exception) {
                showError("Customer check failed: ${e.message}")
            }
        }
    }

    private fun handleGetUserType() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText("Getting user type and card status...\n\nChecking authentication requirements...")
                val cardsStatus = session.getUserType()
                when (cardsStatus?.statusCode) {
                    StatusCode.RECOGNIZED_CARDS_PRESENT -> {
                        updateResultText(
                            "Cards available without authentication!\n\n" +
                                    "Status: RECOGNIZED_CARDS_PRESENT\n" +
                                    "Customer can access cards directly.\n\n" +
                                    "Next: Click 'Get Recognized Cards'"
                        )
                        btnGetCards.isEnabled = true
                        needsOTPValidation = false
                    }

                    StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION -> {
                        updateResultText(
                            "Authentication required!\n\n" +
                                    "Status: TRIGGERED_CUSTOMER_AUTHENTICATION\n" +
                                    "OTP has been sent to customer.\n\n" +
                                    "Next: Click 'Validate OTP'"
                        )
                        btnValidateOTP.visibility = VISIBLE
                        btnValidateOTP.isEnabled = true
                        needsOTPValidation = true
                    }

                    else -> {
                        showError("Unexpected status: ${cardsStatus?.statusCode}")
                    }
                }
            } catch (e: Exception) {
                showError("Get user type failed: ${e.message}")
            }
        }
    }

    private fun handleValidateOTP() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 6-digit OTP"
        }

        AlertDialog.Builder(this)
            .setTitle("Verify Your Identity")
            .setMessage("Please enter the OTP sent to your registered contact")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val otp = input.text.toString()
                if (otp.isEmpty()) {
                    Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        updateResultText("Validating OTP...\n\nVerifying authentication code...")
                        recognizedCards = session.validateCustomerAuthentication(otp)
                        updateResultText(
                            "OTP validated successfully!\n\n" +
                                    "Found ${recognizedCards?.size ?: 0} recognized card(s).\n\n" +
                                    "Next: Click 'Get Recognized Cards' to view them"
                        )
                        btnValidateOTP.visibility = GONE
                        btnGetCards.isEnabled = true
                    } catch (e: ClickToPayException) {
                        showError("Invalid OTP: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleGetCards() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText("Fetching recognized cards...\n\nRetrieving card list from Click to Pay...")

                if (!needsOTPValidation || recognizedCards != null) {
                    recognizedCards = session.getRecognizedCards()
                }

                val cards = recognizedCards
                if (cards.isNullOrEmpty()) {
                    showError("No cards found")
                    return@launch
                }

                val cardsList = cards.mapIndexed { index, card ->
                    "${index + 1}. **** **** **** ${card.panLastFour} (${card.paymentCardType ?: "Unknown"})"
                }.joinToString("\n")

                updateResultText(
                    "Found ${cards.size} recognized card(s)!\n\n" +
                            cardsList + "\n\n" +
                            "Next: Click 'Select Card for Checkout'"
                )
                btnSelectCard.isEnabled = true
            } catch (e: Exception) {
                showError("Get cards failed: ${e.message}")
            }
        }
    }

    private fun handleSelectCard() {
        val cards = recognizedCards
        if (cards.isNullOrEmpty()) {
            showError("No cards available")
            return
        }

        val cardLabels = cards.map { "**** **** **** ${it.panLastFour}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Card for Checkout")
            .setItems(cardLabels) { _, which ->
                selectedCard = cards[which]
                updateResultText(
                    "Card selected!\n\n" +
                            "Selected: **** **** **** ${selectedCard?.panLastFour}\n" +
                            "Type: ${selectedCard?.paymentCardType ?: "Unknown"}\n" +
                            "ID: ${selectedCard?.srcDigitalCardId}\n\n" +
                            "Next: Click 'Checkout with Selected Card'"
                )
                btnCheckout.isEnabled = true
            }
            .show()
    }

    private fun handleCheckout() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        val card = selectedCard ?: run {
            showError("No card selected")
            return
        }

        lifecycleScope.launch {
            try {
                updateResultText(
                    "Processing checkout...\n\n" +
                            "Card: **** **** **** ${card.panLastFour}\n" +
                            "Requesting payment authorization..."
                )

                val response = session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))

                if (response?.status == AuthenticationStatus.SUCCESS) {
                    val vaultInfo = when (val vault = response.vaultTokenData) {
                        is PaymentData.CardData -> "Card Number: ${vault.cardNumber}"
                        is PaymentData.NetworkTokenData -> "Network Token: ${vault.networkToken}"
                        null -> "No vault token data"
                    }

                    updateResultText(
                        "PAYMENT SUCCESSFUL!\n\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                                "Card: **** **** **** ${card.panLastFour}\n" +
                                "Amount: ${response.amount} ${response.currency}\n" +
                                "Status: ${response.transStatus}\n" +
                                "Auth Status: ${response.status}\n\n" +
                                "$vaultInfo\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                                "Transaction completed successfully!"
                    )
                    Toast.makeText(this@ClickToPayExample, "Payment completed!", Toast.LENGTH_SHORT).show()
                } else {
                    showError("Payment failed: ${response?.status}")
                }
            } catch (e: ClickToPayException) {
                when (e.type) {
                    ClickToPayErrorType.CHANGE_CARD -> {
                        Toast.makeText(
                            this@ClickToPayExample,
                            "Card change detected. Please select the same card.",
                            Toast.LENGTH_LONG
                        ).show()
                        updateResultText(
                            "Card Change Error\n\n" +
                                    "You cannot change the card during checkout.\n" +
                                    "Please select the same card or reset the flow.\n\n" +
                                    "Error: ${e.reason}"
                        )
                    }

                    ClickToPayErrorType.SWITCH_CONSUMER -> {
                        Toast.makeText(
                            this@ClickToPayExample,
                            "Consumer switch detected. Cannot proceed.",
                            Toast.LENGTH_LONG
                        ).show()
                        updateResultText(
                            "Consumer Switch Error\n\n" +
                                    "You cannot switch users during checkout.\n" +
                                    "Please reset the flow.\n\n" +
                                    "Error: ${e.reason}"
                        )
                    }

                    else -> {
                        showError("Checkout error: ${e.reason}")
                    }
                }
            } catch (e: Exception) {
                showError("Checkout failed: ${e.message}")
            }
        }
    }

    private fun handleSignOut() {
        val session = clickToPaySession ?: run {
            showError("Click to Pay session not initialized")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out from Click to Pay?")
            .setPositiveButton("Sign Out") { _, _ ->
                lifecycleScope.launch {
                    try {
                        updateResultText("Signing out...\n\nDisconnecting from Click to Pay...")
                        val response = session.signOut()
                        if (response.recognized == false) {
                            updateResultText(
                                "Signed out successfully!\n\n" +
                                        "Customer is no longer recognized.\n" +
                                        "Response: $response\n\n" +
                                        "Click 'Reset Flow' to start over."
                            )
                            disableAllButtons()
                        } else {
                            showError("Sign out may not have completed properly")
                        }
                    } catch (e: Exception) {
                        showError("Sign out failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Flow")
            .setMessage("This will reset the entire Click to Pay flow. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                resetFlow()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private fun resetFlow() {
        credentials = null
        authSession = null
        clickToPaySession = null
        recognizedCards = null
        selectedCard = null
        needsOTPValidation = false

        btnGetCredentials.isEnabled = true
        btnInitAuth.isEnabled = false
        btnInitC2P.isEnabled = false
        btnCheckCustomer.isEnabled = false
        btnGetUserType.isEnabled = false
        btnValidateOTP.isEnabled = false
        btnValidateOTP.visibility = GONE
        btnGetCards.isEnabled = false
        btnSelectCard.isEnabled = false
        btnCheckout.isEnabled = false
        btnSignOut.isEnabled = false

        updateResultText("Flow reset complete!\n\nClick 'Get Authentication Credentials' to start a new flow.")
    }

    private fun disableAllButtons() {
        btnGetCredentials.isEnabled = false
        btnInitAuth.isEnabled = false
        btnInitC2P.isEnabled = false
        btnCheckCustomer.isEnabled = false
        btnGetUserType.isEnabled = false
        btnValidateOTP.isEnabled = false
        btnGetCards.isEnabled = false
        btnSelectCard.isEnabled = false
        btnCheckout.isEnabled = false
        btnSignOut.isEnabled = false
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateResultText("Error: $message\n\nPlease check your configuration or reset the flow.")
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
