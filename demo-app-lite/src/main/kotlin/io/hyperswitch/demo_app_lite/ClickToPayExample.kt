package io.hyperswitch.demo_app_lite

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler as OSHandler
import android.os.Looper
import android.text.InputType
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
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

        btnStart.text = "Start Click to Pay Flow"
        btnStart.setOnClickListener { startClickToPayFlow() }
        btnClose.setOnClickListener { closeSession() }

        updateResultText("Click 'Start Click to Pay Flow' to begin")
        
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

    private fun startClickToPayFlow() {
        btnStart.isEnabled = false
        if (SessionHolder.clickToPaySession != null) {
            lifecycleScope.launch {
                try {
                    SessionHolder.clickToPaySession?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                SessionHolder.clickToPaySession = null
                clickToPaySession = null
                updateResultText("Initializing Click to Pay (Fresh)...")
                getAuthenticationCredentials()
            }
        } else {
            updateResultText("Initializing Click to Pay...")
            getAuthenticationCredentials()
        }
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
                if (SessionHolder.clickToPaySession != null) {
                    // Reuse existing session
                    clickToPaySession = SessionHolder.clickToPaySession
                    
                    // Attach to this activity
                    clickToPaySession?.clickToPaySessionLauncher?.setActivity(this@ClickToPayExample)
                    
                    val startTime = System.currentTimeMillis()
                    // Re-initialize using existing session
                    clickToPaySession?.initClickToPaySession(
                        credentials.clientSecret,
                        credentials.profileId,
                        credentials.authenticationId,
                        credentials.merchantId,
                        false
                    )
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("ClickToPayTiming", "ClickToPayExample initClickToPaySession (reused) took ${duration}ms")
                    updateResultText("Session reused (${duration}ms)\nChecking customer...")
                } else {
                    // Create new session
                    val authSession =
                        AuthenticationSession(this@ClickToPayExample, credentials.publishableKey)
                    authSession.initAuthenticationSession(
                        credentials.clientSecret,
                        credentials.profileId,
                        credentials.authenticationId,
                        credentials.merchantId
                    )
                    val startTime = System.currentTimeMillis()
                    clickToPaySession = authSession.initClickToPaySession()
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("ClickToPayTiming", "ClickToPayExample initClickToPaySession took ${duration}ms")
                    updateResultText("Sessions initialized (${duration}ms)\nChecking customer...")
                }
                
                btnClose.visibility = VISIBLE

                // Step 4: Check customer presence
                val customerPresent = clickToPaySession?.isCustomerPresent(CustomerPresenceRequest())
                if (customerPresent?.customerPresent != true) {
                    showError("Customer not found in Click to Pay")
                    return@launch
                }
                updateResultText("Customer found\nLaunching next activity...")

                // Store session for reuse
                SessionHolder.clickToPaySession = clickToPaySession

                // Important: Don't close or destroy the session when leaving this activity
                // But we should detach the WebView so it can be re-attached in the next activity
                // However, the current SDK API doesn't expose detachWebView directly.
                // It will be handled when C2PExample2 calls setActivity().
                // But to be safe and avoid leaks, we should probably nullify our reference here
                clickToPaySession = null

                val intent = Intent(this@ClickToPayExample, C2PExample2::class.java).apply {
                    putExtra("publishableKey", credentials.publishableKey)
                    putExtra("clientSecret", credentials.clientSecret)
                    putExtra("profileId", credentials.profileId)
                    putExtra("authenticationId", credentials.authenticationId)
                    putExtra("merchantId", credentials.merchantId)
                }
                startActivity(intent)

            } catch(e: Exception){
                e.printStackTrace()
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
                    btnStart.isEnabled = true
                    updateResultText("✓ Session closed successfully\n\nClick 'Start Click to Pay Flow' to begin again")
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


