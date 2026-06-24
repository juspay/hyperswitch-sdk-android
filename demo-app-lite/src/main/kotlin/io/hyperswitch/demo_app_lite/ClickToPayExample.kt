package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler as OSHandler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import io.hyperswitch.paymentsheet.PaymentSheet
import kotlinx.coroutines.launch
import org.json.JSONObject

class ClickToPayExample : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnInit: Button
    private lateinit var btnInitC2P: Button
    private lateinit var btnCheckCustomer: Button
    private lateinit var btnGetCards: Button
    private lateinit var btnShowCards: Button
    private lateinit var btnValidateOTP: Button
    private lateinit var btnCheckout: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnClose: Button
    private lateinit var btnNewActivity: Button
    private lateinit var frameCounter: TextView
    private lateinit var freezeStatus: TextView
    private lateinit var freezeLog: TextView
    private var recognizedCards: List<RecognizedCard>? = null
    private var clickToPaySession: ClickToPaySession? = null
    private var credentials: Credentials? = null
    private var authSession: AuthenticationSession? = null
    private val serverURL = "http://10.0.2.2:5252"

    // Freeze detection
    private val mainHandler = OSHandler(Looper.getMainLooper())
    private var frameCount = 0L
    private var lastUpdateTime = 0L
    private var freezeCount = 0
    private var maxFreezeDuration = 0L
    private val freezeEvents = mutableListOf<String>()

    private val freezeDetectionRunnable = object : Runnable {
        override fun run() {
            frameCount++
            frameCounter.text = frameCount.toString()

            val currentTime = System.currentTimeMillis()
            val timeSinceLastUpdate = currentTime - lastUpdateTime

            if (lastUpdateTime > 0 && timeSinceLastUpdate > 200) {
                freezeCount++
                if (timeSinceLastUpdate > maxFreezeDuration) {
                    maxFreezeDuration = timeSinceLastUpdate
                }

                val freezeEvent = "Freeze #$freezeCount: ${timeSinceLastUpdate}ms"
                freezeEvents.add(freezeEvent)

                if (freezeEvents.size > 5) {
                    freezeEvents.removeAt(0)
                }

                freezeStatus.text = "⚠ UI freeze detected (${timeSinceLastUpdate}ms gap)"
                freezeStatus.setTextColor(android.graphics.Color.parseColor("#dc3545"))

                updateFreezeLog()
            } else {
                freezeStatus.text = "✓ UI is responsive"
                freezeStatus.setTextColor(android.graphics.Color.parseColor("#28a745"))
            }

            lastUpdateTime = currentTime
            mainHandler.postDelayed(this, 100)
        }
    }

    private fun updateFreezeLog() {
        val logText = buildString {
            append("Total Freezes: $freezeCount")
            if (maxFreezeDuration > 0) {
                append(" | Max: ${maxFreezeDuration}ms")
            }
            if (freezeEvents.isNotEmpty()) {
                append("\nRecent: ${freezeEvents.takeLast(3).joinToString(", ")}")
            }
        }
        freezeLog.text = logText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)

        resultText = findViewById(R.id.resultText)
        btnInit = findViewById(R.id.btnStep1)
        btnInitC2P = findViewById(R.id.btnInitC2P)
        btnCheckCustomer = findViewById(R.id.btnCheckCustomer)
        findViewById<Button>(R.id.btnUseExistingAuth).visibility = View.GONE
        findViewById<Button>(R.id.btnGetActiveC2P).visibility = View.GONE
        btnGetCards = findViewById(R.id.btnGetCards)
        btnShowCards = findViewById(R.id.btnShowCards)
        btnValidateOTP = findViewById(R.id.btnValidateOTP)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnSignOut = findViewById(R.id.signOut)
        btnClose = findViewById(R.id.btnClose)
        btnNewActivity = findViewById(R.id.btnNewActivity)
        frameCounter = findViewById(R.id.frameCounter)
        freezeStatus = findViewById(R.id.freezeStatus)
        freezeLog = findViewById(R.id.freezeLog)

        btnInit.text = "1. Init Auth Session"
        btnInit.setOnClickListener { initializeSession() }
        btnInitC2P.setOnClickListener { initC2PSession() }
        btnCheckCustomer.setOnClickListener { checkCustomer() }
        btnGetCards.setOnClickListener { getCards() }
        btnShowCards.setOnClickListener { showCards() }
        btnValidateOTP.setOnClickListener { validateOTP() }
        btnCheckout.setOnClickListener { doCheckout() }
        btnSignOut.setOnClickListener { signOut() }
        btnClose.setOnClickListener { closeSession() }
        btnNewActivity.setOnClickListener { startNewActivity() }

        updateResultText("Click individual buttons to test C2P operations")

        startFreezeDetection()
    }

    private fun startFreezeDetection() {
        lastUpdateTime = System.currentTimeMillis()
        mainHandler.post(freezeDetectionRunnable)
    }

    private fun stopFreezeDetection() {
        mainHandler.removeCallbacks(freezeDetectionRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFreezeDetection()
    }

    // --- Individual C2P Operations ---

    private fun initializeSession() {
        updateResultText("Initializing Click to Pay...")
        getAuthenticationCredentials()
    }

    private fun checkCustomer() {
        lifecycleScope.launch {
            try {
                updateResultText("Checking customer presence...")
                val customerPresent = clickToPaySession?.isCustomerPresent(CustomerPresenceRequest())
                updateResultText("Customer present: ${customerPresent?.customerPresent}")
            } catch (e: ClickToPayException) {
                showError("Check customer failed: ${e.message}")
            } catch (e: Exception) {
                showError("Check customer error: ${e.message}")
            }
        }
    }

    private fun getCards() {
        lifecycleScope.launch {
            try {
                updateResultText("Getting user type...")
                val cardsStatus = clickToPaySession?.getUserType()
                updateResultText("User type: ${cardsStatus?.statusCode}")

                if (cardsStatus?.statusCode == StatusCode.RECOGNIZED_CARDS_PRESENT) {
                    updateResultText("Getting recognized cards...")
                    recognizedCards = clickToPaySession?.getRecognizedCards()
                    updateResultText("Found ${recognizedCards?.size ?: 0} card(s)")
                } else if (cardsStatus?.statusCode == StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION) {
                    updateResultText("Customer authentication required. Use Validate OTP.")
                }
            } catch (e: ClickToPayException) {
                showError("Get cards failed: ${e.message}")
            } catch (e: Exception) {
                showError("Get cards error: ${e.message}")
            }
        }
    }

    private fun showCards() {
        val cards = recognizedCards
        if (cards.isNullOrEmpty()) {
            showError("No cards available. Call Get Cards first.")
            return
        }
        clickToPaySession?.let {
            AlertDialog.Builder(this)
                .setTitle("Select Card")
                .setItems(cards.map { "**** ${it.panLastFour}" }.toTypedArray()) { _, which ->
                    updateResultText("Selected card: **** ${cards[which].panLastFour}")
                }
                .show()
        } ?: showError("No active session")
    }

    private fun validateOTP() {
        clickToPaySession?.let { session ->
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
                            recognizedCards = session.validateCustomerAuthentication(input.text.toString())
                            updateResultText("OTP validated. ${recognizedCards?.size ?: 0} card(s) available")
                        } catch (e: ClickToPayException) {
                            showError("Invalid OTP: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } ?: showError("No active session")
    }

    private fun doCheckout() {
        val cards = recognizedCards
        if (cards.isNullOrEmpty()) {
            showError("No cards available. Call Get Cards first.")
            return
        }
        clickToPaySession?.let { session ->
            AlertDialog.Builder(this)
                .setTitle("Select Card to Checkout")
                .setItems(cards.map { "**** ${it.panLastFour}" }.toTypedArray()) { _, which ->
                    checkout(session, cards[which])
                }
                .show()
        } ?: showError("No active session")
    }

    private fun checkout(session: ClickToPaySession, card: RecognizedCard) {
        lifecycleScope.launch {
            try {
                updateResultText("Processing payment...")
                val response = session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))

                if (response.status == AuthenticationStatus.SUCCESS) {
                    updateResultText(
                        "✓ Payment Successful!\n\nCard: **** ${card.panLastFour}\n" +
                                "Amount: ${response.amount} ${response.currency}\nStatus: ${response.transStatus}"
                    )
                    Toast.makeText(this@ClickToPayExample, "Payment completed!", Toast.LENGTH_SHORT).show()
                } else {
                    showError("Payment failed")
                }
            } catch (e: ClickToPayException) {
                when (e.type) {
                    ClickToPayErrorType.CHANGE_CARD -> {
                        Toast.makeText(this@ClickToPayExample, "You should not change card", Toast.LENGTH_LONG).show()
                        showCardSelection(session, "You cannot change card, Select card")
                    }
                    ClickToPayErrorType.SWITCH_CONSUMER -> {
                        Toast.makeText(this@ClickToPayExample, "You should not change user", Toast.LENGTH_LONG).show()
                        showCardSelection(session, "You cannot change user, select card")
                    }
                    else -> showError("Checkout error: ${e.reason}")
                }
            }
        }
    }

    private fun showCardSelection(session: ClickToPaySession, errorMessage: String? = "") {
        val cards = recognizedCards ?: return
        AlertDialog.Builder(this)
            .setTitle(errorMessage ?: "Select Card")
            .setItems(cards.map { "**** ${it.panLastFour}" }.toTypedArray()) { _, which ->
                checkout(session, cards[which])
            }
            .show()
    }

    private fun signOut() {
        lifecycleScope.launch {
            try {
                updateResultText("Signing out...")
                val response = clickToPaySession?.signOut()
                updateResultText("Sign out response: $response")
            } catch (e: Exception) {
                showError("Cannot signout: ${e.message}")
            }
        }
    }

    private fun closeSession() {
        lifecycleScope.launch {
            try {
                updateResultText("Closing session...")
                clickToPaySession?.close()
                clickToPaySession = null
                recognizedCards = null
                credentials = null
                updateResultText("✓ Session closed")
                Toast.makeText(this@ClickToPayExample, "Session closed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to close session: ${e.message}")
            }
        }
    }

    private fun startNewActivity() {
        val intent = Intent(this, ClickToPayExample2::class.java)
        credentials?.let { creds ->
            intent.putExtra("publishableKey", creds.publishableKey)
            intent.putExtra("clientSecret", creds.clientSecret)
            intent.putExtra("profileId", creds.profileId)
            intent.putExtra("authenticationId", creds.authenticationId)
            intent.putExtra("merchantId", creds.merchantId)
        }
        startActivity(intent)
    }

    // --- Auth & Init ---

    private fun getAuthenticationCredentials() {
        Fuel.post("$serverURL/create-authentication")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .body("{}")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
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
                            initAuthentication()
                        } else {
                            updateResultText("Failed: Create Authentication Result is null")
                        }
                    } catch (e: Exception) {
                        updateResultText("Failed: ${e.message}")
                    }
                }

                override fun failure(error: FuelError) {
                    updateResultText("Failed: ${error.message}")
                }
            })
    }

    private fun initAuthentication() {
        lifecycleScope.launch {
            try {
                val creds = credentials ?: run {
                    showError("No credentials. Initialize first.")
                    return@launch
                }
                val session = AuthenticationSession(this@ClickToPayExample, creds.publishableKey)
                authSession = session
                session.initAuthenticationSession(
                    creds.clientSecret,
                    creds.profileId,
                    creds.authenticationId,
                    creds.merchantId
                )
                updateResultText("✓ Auth session initialized")
            } catch (e: Exception) {
                showError("${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun initC2PSession() {
        lifecycleScope.launch {
            try {
                val session = authSession ?: run {
                    showError("No auth session. Call Init Auth Session first.")
                    return@launch
                }
                val start = System.currentTimeMillis()
                clickToPaySession = session.initClickToPaySession()
                updateResultText("✓ C2P session initialized")
            } catch (e: Exception) {
                showError("${e.message}")
                e.printStackTrace()
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
