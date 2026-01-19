package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.content.Intent
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
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.click_to_pay.ClickToPaySession
import io.hyperswitch.click_to_pay.models.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis

class ClickToPayExample : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var btnStart: Button
    private lateinit var signOut: Button
    private lateinit var btnClose: Button
    private lateinit var frameCounter: TextView
    private lateinit var freezeStatus: TextView
    private lateinit var freezeLog: TextView
    private var recognizedCards: List<RecognizedCard>? = null
    private var clickToPaySession: ClickToPaySession? = null
    
    // Test Data
    private var savedCredentials: Credentials? = null
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

        btnStart.text = "Select Test Flow"
        btnStart.setOnClickListener { showFlowSelectionDialog() }
        btnClose.setOnClickListener { closeSession() }

        updateResultText("Select a test flow to begin")
        
        // Start freeze detection
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

    private fun showFlowSelectionDialog() {
        val options = arrayOf(
            "Flow 1: Normal (Init & Save Creds)",
            "Flow 2: Resume Session (Reuse Creds - New Activity)",
            "Flow 3: New User (New Creds & Fallback - New Activity)"
        )
        AlertDialog.Builder(this)
            .setTitle("Select Flow")
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> runFlow1()
                        1 -> runFlow2()
                        2 -> runFlow3()
                    }
                }
            }
            .show()
    }

    private fun clearLogs() {
        operationLogs.clear()
        updateResultText("")
    }

    private suspend fun logOperation(name: String, block: suspend () -> Unit) {
        val time = measureTimeMillis {
            block()
        }
        val log = "$name took ${time}ms"
        operationLogs.append(log).append("\n")
        updateResultText(operationLogs.toString())
    }

    // Flow 1: Normal Init
    private suspend fun runFlow1() {
        clearLogs()
        btnStart.isEnabled = false
        try {
            var credentials: Credentials? = null
            logOperation("Fetch Credentials") {
                credentials = fetchCredentials()
                savedCredentials = credentials // Save for Flow 2
            }
            val creds = credentials ?: return

            val authSession = AuthenticationSession(this@ClickToPayExample, creds.publishableKey)
            
            logOperation("Init Authentication Session") {
                authSession.initAuthenticationSession(
                    creds.clientSecret,
                    creds.profileId,
                    creds.authenticationId,
                    creds.merchantId
                )
            }

            logOperation("Init Click to Pay Session") {
                clickToPaySession = authSession.initClickToPaySession()
            }

            checkCustomerAndProceed()
        } catch (e: Exception) {
            updateResultText("Flow 1 Failed: ${e.message}\n\nLogs:\n$operationLogs")
            e.printStackTrace()
        } finally {
            btnStart.isEnabled = true
        }
    }

    // Flow 2: Resume Session (Use Case 2) - New Activity
    private suspend fun runFlow2() {
        clearLogs()
        val creds = savedCredentials
        if (creds == null) {
            updateResultText("No saved credentials! Run Flow 1 first.")
            return
        }
        
        launchResultActivity(creds, "Flow 2: Resume Session")
    }

    // Flow 3: New User (Use Case 3) - New Activity
    private suspend fun runFlow3() {
        clearLogs()
        btnStart.isEnabled = false
        try {
            var credentials: Credentials? = null
            logOperation("Fetch NEW Credentials") {
                credentials = fetchCredentials()
            }
            val creds = credentials ?: return

            launchResultActivity(creds, "Flow 3: New User")
        } catch (e: Exception) {
            updateResultText("Flow 3 Failed: ${e.message}")
            e.printStackTrace()
        } finally {
            btnStart.isEnabled = true
        }
    }
    
    private fun launchResultActivity(creds: Credentials, flowName: String) {
        val intent = Intent(this, ClickToPayResultActivity::class.java).apply {
            putExtra("FLOW_NAME", flowName)
            putExtra("publishableKey", creds.publishableKey)
            putExtra("clientSecret", creds.clientSecret)
            putExtra("profileId", creds.profileId)
            putExtra("authenticationId", creds.authenticationId)
            putExtra("merchantId", creds.merchantId)
        }
        startActivity(intent)
        updateResultText("Launched $flowName in new Activity")
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
        
        // Get Cards
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

    private suspend fun fetchCredentials(): Credentials = suspendCancellableCoroutine { continuation ->
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
                            continuation.resume(credentials)
                        } else {
                            continuation.resumeWithException(Exception("Create Authentication Result is null"))
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun failure(error: FuelError) {
                    continuation.resumeWithException(error)
                }
            })
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
                    updateResultText("✓ Session closed successfully\n\nClick 'Select Test Flow' to begin again")
                    Toast.makeText(this@ClickToPayExample, "Session closed", Toast.LENGTH_SHORT).show()
                } ?: run {
                    updateResultText("No active session to close")
                    Toast.makeText(this@ClickToPayExample, "No active session", Toast.LENGTH_SHORT).show()
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
