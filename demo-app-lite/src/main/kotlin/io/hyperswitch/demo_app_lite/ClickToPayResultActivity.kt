package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler as OSHandler
import android.os.Looper
import android.text.InputType
import android.util.Log
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
import kotlin.system.measureTimeMillis

class ClickToPayResultActivity : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnStart: Button
    private lateinit var signOut: Button
    private lateinit var btnClose: Button
    private lateinit var frameCounter: TextView
    private lateinit var freezeStatus: TextView
    private lateinit var freezeLog: TextView
    private var recognizedCards: List<RecognizedCard>? = null
    private var clickToPaySession: ClickToPaySession? = null
    
    private val operationLogs = StringBuilder()

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
        btnStart = findViewById(R.id.btnStep1)
        signOut = findViewById(R.id.signOut)
        btnClose = findViewById(R.id.btnClose)
        frameCounter = findViewById(R.id.frameCounter)
        freezeStatus = findViewById(R.id.freezeStatus)
        freezeLog = findViewById(R.id.freezeLog)
        
        signOut.visibility = INVISIBLE
        btnClose.visibility = INVISIBLE

        btnStart.text = "Proceed with Session Check"
        btnStart.setOnClickListener { startCheck() }
        btnClose.setOnClickListener { closeSession() }

        val flowName = intent.getStringExtra("FLOW_NAME") ?: "Unknown Flow"
        updateResultText("New Activity Launched for $flowName\n\nClick 'Proceed' to check for active session and continue.")
        
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

    private fun startCheck() {
        btnStart.isEnabled = false
        lifecycleScope.launch {
            runFlow()
        }
    }

    private suspend fun logOperation(name: String, block: suspend () -> Unit) {
        val time = measureTimeMillis {
            block()
        }
        val log = "$name took ${time}ms"
        operationLogs.append(log).append("\n")
        updateResultText(operationLogs.toString())
    }

    private suspend fun runFlow() {
        try {
            val publishableKey = intent.getStringExtra("publishableKey") ?: return
            val clientSecret = intent.getStringExtra("clientSecret") ?: return
            val profileId = intent.getStringExtra("profileId") ?: return
            val authenticationId = intent.getStringExtra("authenticationId") ?: return
            val merchantId = intent.getStringExtra("merchantId") ?: return

            val authSession = AuthenticationSession(this, publishableKey)

            logOperation("Init Authentication Session") {
                authSession.initAuthenticationSession(
                    clientSecret,
                    profileId,
                    authenticationId,
                    merchantId
                )
            }

            logOperation("Get Active C2P Session") {
                clickToPaySession = authSession.getActiveClickToPaySession(this@ClickToPayResultActivity)
            }

            if (clickToPaySession != null) {
                operationLogs.append("✓ Active session found! (Persistence worked)\n")
            } else {
                operationLogs.append("ℹ No active session found.\n")
                logOperation("Fallback: Init Click to Pay Session") {
                    clickToPaySession = authSession.initClickToPaySession()
                }
            }

            checkCustomerAndProceed()

        } catch (e: Exception) {
            updateResultText("Flow Failed: ${e.message}\n\nLogs:\n$operationLogs")
            e.printStackTrace()
            btnStart.isEnabled = true
        }
    }

    private suspend fun checkCustomerAndProceed() {
        logOperation("Check Customer Presence") {
            val customerPresent = clickToPaySession?.isCustomerPresent(CustomerPresenceRequest())
            if (customerPresent?.customerPresent == true) {
                operationLogs.append("✓ Customer Present\n")
            } else {
                operationLogs.append("✗ Customer Not Found\n")
            }
        }
        
        signOut.visibility = VISIBLE
        btnClose.visibility = VISIBLE
        signOut.setOnClickListener {
            clickToPaySession?.let { signOut(it) }
        }
        
        logOperation("Get User Type") {
             val cardsStatus = clickToPaySession?.getUserType()
             when (cardsStatus?.statusCode) {
                StatusCode.RECOGNIZED_CARDS_PRESENT -> {
                    recognizedCards = clickToPaySession?.getRecognizedCards()
                    clickToPaySession?.let { showCardSelection(it) }
                }
                StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION -> {
                    clickToPaySession?.let { showOTPDialog(it) }
                }
                else -> {
                    showError("No cards found")
                }
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
                        logOperation("Validate OTP") {
                             recognizedCards = session.validateCustomerAuthentication(input.text.toString())
                        }
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
        updateResultText("✓ Found ${cards.size} card(s)\nSelect a card to checkout\n\nLogs:\n$operationLogs")

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
                var response: CheckoutResponse? = null
                logOperation("Checkout with Card") {
                    response = session.checkoutWithCard(CheckoutRequest(card.srcDigitalCardId, false))
                }

                if (response?.status == AuthenticationStatus.SUCCESS) {

                    when (val vault = response?.vaultTokenData) {
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
                                "Amount: ${response?.amount} ${response?.currency}\nStatus: ${response?.transStatus}\n\nLogs:\n$operationLogs"
                    )
                    Toast.makeText(this@ClickToPayResultActivity, "Payment completed!", Toast.LENGTH_SHORT)
                        .show()

                } else {
                    showError("Payment failed")
                    signOut.visibility = INVISIBLE
                }
            } catch (e: ClickToPayException) {
                if (e.type == ClickToPayErrorType.CHANGE_CARD){
                    Toast.makeText(this@ClickToPayResultActivity,"You should not change card", Toast.LENGTH_LONG ).show()
                    showCardSelection(session, "You cannot change card, Select card")
                } else if (e.type == ClickToPayErrorType.SWITCH_CONSUMER){
                    Toast.makeText(this@ClickToPayResultActivity,"You should not change user", Toast.LENGTH_LONG ).show()
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
                logOperation("Sign Out") {
                    val response = session.signOut()
                    if (response.recognized == false) {
                        signOut.visibility = INVISIBLE
                        operationLogs.append("Sign out success: ${response}\n")
                    }
                }
            } catch (e: Exception){
                showError("Cannot signout")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateResultText("Error: $message\n\nLogs:\n$operationLogs")
    }

    private fun closeSession() {
        lifecycleScope.launch {
            try {
                clickToPaySession?.let { session ->
                    logOperation("Close Session") {
                        session.close()
                    }
                    clickToPaySession = null
                    recognizedCards = null
                    signOut.visibility = INVISIBLE
                    btnClose.visibility = INVISIBLE
                    btnStart.isEnabled = true
                    updateResultText("✓ Session closed successfully")
                    Toast.makeText(this@ClickToPayResultActivity, "Session closed", Toast.LENGTH_SHORT).show()
                } ?: run {
                    updateResultText("No active session to close")
                    Toast.makeText(this@ClickToPayResultActivity, "No active session", Toast.LENGTH_SHORT).show()
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
}
