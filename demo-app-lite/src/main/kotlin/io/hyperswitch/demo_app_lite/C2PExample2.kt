package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler as OSHandler
import android.os.Looper
import android.text.InputType
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.click_to_pay.ClickToPaySession
import io.hyperswitch.click_to_pay.models.*
import kotlinx.coroutines.launch

class C2PExample2 : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnStart: Button
    private lateinit var signOut: Button
    private lateinit var btnClose: Button
    private lateinit var frameCounter: TextView
    private lateinit var freezeStatus: TextView
    private lateinit var freezeLog: TextView
    private var recognizedCards: List<RecognizedCard>? = null
    private var clickToPaySession: ClickToPaySession? = null

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

            // If more than 200ms has passed, UI might be freezing
            if (lastUpdateTime > 0 && timeSinceLastUpdate > 200) {
                freezeCount++
                if (timeSinceLastUpdate > maxFreezeDuration) {
                    maxFreezeDuration = timeSinceLastUpdate
                }

                val freezeEvent = "Freeze #$freezeCount: ${timeSinceLastUpdate}ms"
                freezeEvents.add(freezeEvent)

                // Keep only last 5 freeze events
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
            mainHandler.postDelayed(this, 100) // Update every 100ms
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
        btnStart = findViewById(R.id.btnStep1)
        signOut = findViewById(R.id.signOut)
        btnClose = findViewById(R.id.btnClose)
        frameCounter = findViewById(R.id.frameCounter)
        freezeStatus = findViewById(R.id.freezeStatus)
        freezeLog = findViewById(R.id.freezeLog)

        signOut.visibility = INVISIBLE
        btnClose.visibility = INVISIBLE

        // In this activity, the flow continues automatically, so we disable the start button or repurpose it.
        btnStart.text = "Processing..."
        btnStart.isEnabled = false
        
        btnClose.setOnClickListener { closeSession() }

        // Start freeze detection
        startFreezeDetection()

        // Get credentials from Intent
        val publishableKey = intent.getStringExtra("publishableKey")
        val clientSecret = intent.getStringExtra("clientSecret")
        val profileId = intent.getStringExtra("profileId")
        val authenticationId = intent.getStringExtra("authenticationId")
        val merchantId = intent.getStringExtra("merchantId")

        if (publishableKey != null && clientSecret != null && profileId != null && authenticationId != null && merchantId != null) {
            val credentials = Credentials(
                publishableKey, clientSecret, profileId, authenticationId, merchantId
            )
            continueClickToPayFlow(credentials)
        } else {
            updateResultText("Error: Missing credentials")
        }
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

    private fun continueClickToPayFlow(credentials: Credentials) {
        updateResultText("Resuming Click to Pay Session (Phase 2)...")
        lifecycleScope.launch {
            try {
                var startTime = System.currentTimeMillis()
                // Retrieve session from SessionHolder
                clickToPaySession = SessionHolder.clickToPaySession
                
                if (clickToPaySession != null) {
                    val startTime = System.currentTimeMillis()
                    // Update the activity context in the session launcher
                    clickToPaySession?.clickToPaySessionLauncher?.setActivity(this@C2PExample2)
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("ClickToPayTiming", "C2PExample2 session reuse setup took ${duration}ms")
                    updateResultText("✓ Session resumed (${duration}ms)\nRetrieving cards...")
                } else {
                    // Fallback if session is lost (e.g., process death)
                    android.util.Log.d("ClickToPayTiming", "Session lost, re-initializing")
                    val startTime = System.currentTimeMillis()
                    val authSession = AuthenticationSession(this@C2PExample2, credentials.publishableKey)
                    authSession.initAuthenticationSession(
                        credentials.clientSecret,
                        credentials.profileId,
                        credentials.authenticationId,
                        credentials.merchantId
                    )
                    clickToPaySession = authSession.initClickToPaySession()
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("ClickToPayTiming", "C2PExample2 session reuse setup took ${duration}ms")
                }
                
                signOut.visibility = VISIBLE
                btnClose.visibility = VISIBLE
                signOut.setOnClickListener {
                    clickToPaySession?.let { signOut(it) }
                }
                var duration = System.currentTimeMillis()  - startTime


                updateResultText("✓ Session initialized ($duration ms)\nRetrieving cards...")

                // Resume flow: Get cards
                startTime = System.currentTimeMillis()
                val cardsStatus = clickToPaySession?.getUserType()
                duration = System.currentTimeMillis()  - startTime
                updateResultText("✓ Retrieved cards ($duration ms)")

                when (cardsStatus?.statusCode) {
                    StatusCode.RECOGNIZED_CARDS_PRESENT -> {
                        recognizedCards = clickToPaySession?.getRecognizedCards()
                        clickToPaySession?.let { showCardSelection(it) }
                    }

                    StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION -> {
                        clickToPaySession?.let { showOTPDialog(it) }
                    }

                    else -> {
                        showError("No cards found or unknown status")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error in Phase 2: ${e.message}")
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
                val startTime = System.currentTimeMillis()
                val response =
                    session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))
                val duration = System.currentTimeMillis() - startTime
                android.util.Log.d("ClickToPayTiming", "C2PExample2 Checkout took ${duration}ms")

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
                    Toast.makeText(this@C2PExample2, "Payment completed!", Toast.LENGTH_SHORT)
                        .show()

                } else {
                    showError("Payment failed")
                    signOut.visibility = INVISIBLE
                }
            } catch (e: ClickToPayException) {
                if (e.type == ClickToPayErrorType.CHANGE_CARD){
                    Toast.makeText(this@C2PExample2,"You should not change card", Toast.LENGTH_LONG ).show()
                    showCardSelection(session, "You cannot change card, Select card")
                } else if (e.type == ClickToPayErrorType.SWITCH_CONSUMER){
                    Toast.makeText(this@C2PExample2,"You should not change user", Toast.LENGTH_LONG ).show()
                    showCardSelection(session, "You cannot change user, select card")
                } else {
                    showError("Checkout error: ${e.reason}")
                    signOut.visibility = INVISIBLE
                }
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

    private fun closeSession() {
        lifecycleScope.launch {
            try {
                clickToPaySession?.let { session ->
                    updateResultText("Closing session...")
                    session.close()
                    clickToPaySession = null
                    recognizedCards = null
                    signOut.visibility = INVISIBLE
                    btnClose.visibility = INVISIBLE
                    updateResultText("✓ Session closed successfully")
                    Toast.makeText(this@C2PExample2, "Session closed", Toast.LENGTH_SHORT).show()
                    // Finish activity to go back to first screen?
                    finish()
                } ?: run {
                    updateResultText("No active session to close")
                    Toast.makeText(this@C2PExample2, "No active session", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showError("Failed to close session: ${e.message}")
                e.printStackTrace()
            }
        }
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
