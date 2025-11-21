package io.hyperswitch.demo_app_threeds

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import io.hyperswitch.threeds.models.ThreeDSEnvironment
import io.hyperswitch.threeds.api.*
import io.hyperswitch.threeds.models.ChallengeParameters
import io.hyperswitch.threeds.provider.ProviderRegistry
import io.hyperswitch.threeds.trident.TridentProviderFactory
import io.hyperswitch.authentication.AuthenticationSession
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private var publishKey: String = ""
    private var paymentIntentClientSecret: String = ""

    // New merchant-facing API objects
    private lateinit var authenticationSession: AuthenticationSession
    private var service: ThreeDSService? = null
    private var transaction: ThreeDSTransaction? = null
    private var aReqParams: AuthenticationRequestParameters? = null
    private var challengeParameters: ChallengeParameters? = null


    private val apiKey = ""
    private val profileId = ""
    private val baseUrl = ""
    private var authenticationId: String? = null
    // Eligibility response data
    private var threeDsServerTransactionId: String? = null
    private var messageVersion: String? = null
    private var directoryServerId: String? = null

    // HTTP client - use singleton to avoid creating multiple instances
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        // Register providers for testing
        ProviderRegistry.registerProvider(TridentProviderFactory())


        authenticationSession = AuthenticationSession(
            activity = this,
            publishableKey = publishKey
        )

        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources to prevent memory leaks
        service = null
        transaction = null
        aReqParams = null
        challengeParameters = null

        Log.d("ThreeDSTest", "Cleared activity references")

        // Cancel any pending HTTP calls
        try {
            httpClient.dispatcher.executorService.shutdown()
        } catch (e: Exception) {
            Log.w("ThreeDSTest", "Error shutting down HTTP client: ${e.message}")
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnInit3DS).setOnClickListener {
            setStatus("Step 1: Initializing Authentication Session...")
            initializeAndCreateAuthentication()
        }

        findViewById<Button>(R.id.btnGenerateAReq).setOnClickListener {
            setStatus("Step 2: Creating Transaction and Generating AReq...")
            createTransactionAndGenerateAReq()
        }

        findViewById<Button>(R.id.btnReceiveChallenge).setOnClickListener {
            setStatus("Step 3: Making Authentication API Call...")
            makeAuthenticationApiCall()
        }

        findViewById<Button>(R.id.btnDoChallenge).setOnClickListener {
            setStatus("Step 4: Performing Challenge...")
            performChallenge()
        }
    }

    private fun initializeAndCreateAuthentication() {
        try {
            setStatus("🔧 Initializing 3DS SDK...")

            // Create 3DS configuration - clean and simple!
            val configuration = io.hyperswitch.threeds.models.ThreeDSConfiguration(
                environment = ThreeDSEnvironment.SANDBOX,
                preferredProvider = io.hyperswitch.threeds.models.ThreeDSProviderType.TRIDENT,
                uiCustomization = null
            )

            authenticationSession.initThreeDSSession(
                clientSecret = paymentIntentClientSecret,
                configuration = configuration
            ) { result ->
                when (result) {
                    is ThreeDSResult.Success -> {
                        service = result.value
                        setStatus("✅ 3DS SDK initialized via Authentication SDK. Making API call to create authentication...")
                        createAuthenticationApiCall()
                    }
                    is ThreeDSResult.Error -> {
                        setStatus("❌ Error: ${result.message}")
                        Log.e("ThreeDSTest", "Init error: ${result.error?.code}")
                    }
                }
            }

        } catch (e: Exception) {
            setStatus("❌ Error initializing session: ${e.message}")
            Log.e("ThreeDSTest", "Error in initializeAndCreateAuthentication", e)
        }
    }

    private fun createAuthenticationApiCall() {
        val requestBody = JSONObject().apply {
            put("amount", 1050)
            put("currency", "EUR")
            put("acquirer_details", JSONObject().apply {
                put("acquirer_merchant_id", "12134")
                put("acquirer_bin", "438309")
                put("merchant_country_code", "004")
            })
            put("authentication_connector", "juspaythreedsserver")
        }

        val request = Request.Builder()
            .url("$baseUrl/authentication")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-Profile-Id", profileId)
            .addHeader("Content-Type", "application/json")
            .addHeader("api-key", apiKey)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setStatus("❌ API Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                response.close()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Log.d("ThreeDSTest", "Auth Create Response: $responseBody")
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "")
                            authenticationId = jsonResponse.getString("authentication_id")
                            setStatus("✅ Authentication created successfully. ID: $authenticationId")
                            // Now call eligibility API
                            callEligibilityApiCall()
                        } catch (e: Exception) {
                            setStatus("❌ Error parsing response: ${e.message}")
                        }
                    } else {
                        setStatus("❌ API Error: ${response.code} - ${response.message}")
                    }
                }
            }
        })
    }

    private fun callEligibilityApiCall() {
        if (authenticationId == null) {
            setStatus("❌ Error: Authentication ID is required for eligibility call")
            return
        }

        setStatus("🔄 Making eligibility API call...")
        // Create request body for eligibility check with proper structure
        val requestBody = JSONObject().apply {
            put("payment_method", "card")
            put("payment_method_data", JSONObject().apply {
                put("card", JSONObject().apply {
                    put("card_number", "5522604003479299")
                    put("card_exp_month", "02")
                    put("card_exp_year", "30")
                    put("card_holder_name", "joseph Doe")
                    put("card_cvc", "666")
                })
            })
            put ("payment_method_type","debit")
            put("billing", JSONObject().apply {
                put("address", JSONObject().apply {
                    put("line1", "1467")
                    put("line2", "Harrison Street")
                    put("line3", "Harrison Street")
                    put("city", "San Fransico")
                    put("state", "CA")
                    put("zip", "94122")
                    put("country", "US")
                    put("first_name", "PiX")
                })
                put("phone", JSONObject().apply {
                    put("number", "123456789")
                    put("country_code", "12")
                })
            })
            put("shipping", JSONObject().apply {
                put("address", JSONObject().apply {
                    put("line1", "1467")
                    put("line2", "Harrison Street")
                    put("line3", "Harrison Street")
                    put("city", "San Fransico")
                    put("state", "California")
                    put("zip", "94122")
                    put("country", "US")
                    put("first_name", "PiX")
                })
                put("phone", JSONObject().apply {
                    put("number", "123456789")
                    put("country_code", "12")
                })
            })
            put("email", "sahkasssslplanet@gmail.com")
            put("browser_information", JSONObject().apply {
                put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36")
                put("accept_header", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                put("language", "nl-NL")
                put("color_depth", 24)
                put("screen_height", 723)
                put("screen_width", 1536)
                put("time_zone", 0)
                put("java_enabled", true)
                put("java_script_enabled", true)
                put("ip_address", "115.99.183.2")
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/authentication/$authenticationId/eligibility")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("api-key", apiKey)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setStatus("❌ Eligibility API Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                response.close()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Log.d("ThreeDSTest", "Eligibility Response: $responseBody")
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "")

                            // Extract eligibility response data
                            val eligibilityParams = jsonResponse.optJSONObject("eligibility_response_params")
                            val threeDsData = eligibilityParams?.optJSONObject("ThreeDsData")

                            if (threeDsData != null) {
                                threeDsServerTransactionId = threeDsData.optString("three_ds_server_transaction_id")
                                messageVersion = threeDsData.optString("message_version")
                                directoryServerId = threeDsData.optString("directory_server_id")

                                setStatus("✅ Eligibility completed - ready for transaction creation")
                            } else {
                                setStatus("✅ Eligibility completed - no 3DS data found")
                            }
                        } catch (e: Exception) {
                            setStatus("❌ Error parsing eligibility response: ${e.message}")
                        }
                    } else {
                        setStatus("❌ Eligibility API Error: ${response.code} - ${response.message}")
                    }
                }
            }
        })
    }

    private fun createTransactionAndGenerateAReq() {
        try {
            if (service == null) {
                setStatus("❌ Error: Please initialize authentication service first")
                return
            }

            if (authenticationId == null) {
                setStatus("❌ Error: Please create authentication first")
                return
            }

            setStatus("🔄 Creating transaction...")

            // Clean callback pattern with when() statement
            service!!.createTransaction(
                directoryServerId = directoryServerId,
                messageVersion = messageVersion ?: "2.2.0",
                cardNetwork = "VISA"
            ) { result ->
                when (result) {
                    is ThreeDSResult.Success -> {
                        transaction = result.value
                        setStatus("🔄 Generating AReq parameters...")

                        // Get authentication request parameters
                        transaction!!.getAuthenticationRequestParameters { paramsResult ->
                            runOnUiThread {
                                when (paramsResult) {
                                    is ThreeDSResult.Success -> {
                                        aReqParams = paramsResult.value
                                        Log.d("areqqdemoapp", aReqParams.toString())
                                        setStatus("✅ AReq generated - SDK Transaction ID: ${paramsResult.value.sdkTransactionID}")
                                    }
                                    is ThreeDSResult.Error -> {
                                        setStatus("❌ Failed to generate AReq: ${paramsResult.message}")
                                        Log.e("ThreeDSTest", "AReq error: ${paramsResult.error?.code}")
                                    }
                                }
                            }
                        }
                    }
                    is ThreeDSResult.Error -> {
                        setStatus("❌ Transaction creation failed: ${result.message}")
                        Log.e("ThreeDSTest", "Transaction error: ${result.error?.code}")
                    }
                }
            }

        } catch (e: Exception) {
            setStatus("❌ Error creating transaction: ${e.message}")
            Log.e("ThreeDSTest", "Error in createTransactionAndGenerateAReq", e)
        }
    }

    private fun makeAuthenticationApiCall() {
        try {
            if (authenticationId == null || aReqParams == null) {
                setStatus("❌ Error: Please complete previous steps first")
                return
            }

            setStatus("🔄 Making authentication API call...")

            val requestBody = JSONObject().apply {
                put("device_channel", "APP")
                put("threeds_method_comp_ind", "N")
                put("sdk_information", JSONObject().apply {
                    put("sdk_app_id", aReqParams!!.sdkAppID)
                    put("sdk_enc_data", aReqParams!!.deviceData) // Map deviceData to sdk_enc_data
                    put("sdk_trans_id", aReqParams!!.sdkTransactionID)
                    put("sdk_reference_number", aReqParams!!.sdkReferenceNumber)
                    put("sdk_max_timeout", 5)

                    // Parse and add sdk_ephem_pub_key if available
                    aReqParams!!.sdkEphemeralPublicKey?.let { ephemeralKey ->
                        try {
                            val ephemeralKeyJson = JSONObject(ephemeralKey)
                            put("sdk_ephem_pub_key", ephemeralKeyJson)
                        } catch (e: Exception) {
                            Log.w("ThreeDSTest", "Failed to parse sdkEphemeralPublicKey: ${e.message}")
                        }
                    }
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/authentication/$authenticationId/authenticate")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", apiKey)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        setStatus("❌ API Error: ${e.message}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    response.close()

                    runOnUiThread {
                        if (response.isSuccessful) {
                            Log.d("ThreeDSTest", "Auth Response: $responseBody")
                            try {
                                val jsonResponse = JSONObject(responseBody ?: "")
                                val transStatus = jsonResponse.optString("trans_status", "")

                                if (transStatus == "C") {
                                    // Challenge required - extract challenge parameters
                                    challengeParameters = ChallengeParameters(
                                        threeDSServerTransactionId = jsonResponse.optString("three_ds_server_transaction_id", ""),
                                        acsTransactionId = jsonResponse.optString("acs_trans_id", ""),
                                        acsReferenceNumber = jsonResponse.optString("acs_reference_number", ""),
                                        acsSignedContent = jsonResponse.optString("acs_signed_content", ""),
                                        threeDSRequestorAppURL = jsonResponse.optString("three_ds_requestor_app_url", ""),
                                        transStatus = transStatus
                                    )
                                    setStatus("✅ Challenge required - Status: $transStatus")
                                } else {
                                    setStatus("✅ Authentication completed - Status: $transStatus")
                                }
                            } catch (e: Exception) {
                                setStatus("❌ Error parsing response: ${e.message}")
                            }
                        } else {
                            setStatus("❌ API Error: ${response.code} - ${response.message}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            setStatus("❌ Error making authentication API call: ${e.message}")
            Log.e("ThreeDSTest", "Error in makeAuthenticationApiCall", e)
        }
    }

    private fun performChallenge() {
        try {
            if (service == null || transaction == null) {
                setStatus("❌ Error: Please complete previous steps first")
                return
            }

            // Check if challenge is required based on transaction status
            if (challengeParameters != null && challengeParameters!!.transStatus == "C") {
                setStatus("🔄 Challenge required, starting challenge flow...")

                val challengeStatusReceiver = object : ChallengeStatusReceiver {
                    override fun completed(completionEvent: CompletionEvent) {
                        setStatus("✅ Challenge completed successfully - Transaction ID: ${completionEvent.transactionId}")
                    }

                    override fun cancelled() {
                        setStatus("🚫 Challenge cancelled by user")
                    }

                    override fun timedout() {
                        setStatus("⏰ Challenge timed out")
                    }

                    override fun protocolError(protocolErrorEvent: ProtocolErrorEvent) {
                        setStatus("❌ Protocol error - Error: ${protocolErrorEvent.errorMessage.errorDescription}")
                    }

                    override fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent) {
                        setStatus("❌ Runtime error - Error: ${runtimeErrorEvent.errorMessage}")
                    }
                }

                runOnUiThread {
                    transaction!!.doChallenge(
                        activity = this@MainActivity,
                        challengeParameters = challengeParameters!!,
                        challengeStatusReceiver = challengeStatusReceiver,
                        timeout = 5
                    )
                }

            } else {
                val status = challengeParameters?.transStatus ?: "unknown"
                setStatus("✅ No challenge required - Status: $status")
            }

        } catch (e: Exception) {
            setStatus("❌ Error performing challenge: ${e.message}")
            Log.e("ThreeDSTest", "Error in performChallenge", e)
        }
    }

    private fun logDiscoveredProviders() {
        try {
            // Import ThreeDSManager to access provider info
            val availableProviders = io.hyperswitch.threeds.ThreeDSManager.getAvailableProviders()
            val providerInfo = io.hyperswitch.threeds.ThreeDSManager.getProviderInfo()

            Log.d("ThreeDSTest", "=== Auto-Discovered 3DS Providers ===")
            Log.d("ThreeDSTest", "Total providers found: ${availableProviders.size}")

            providerInfo.forEach { info ->
                Log.d("ThreeDSTest", "Provider: ${info.type.name}")
                Log.d("ThreeDSTest", "  Version: ${info.version}")
                Log.d("ThreeDSTest", "  Priority: ${info.priority}")
                Log.d("ThreeDSTest", "  Features: ${info.supportedFeatures.joinToString(", ")}")
            }

            if (availableProviders.isEmpty()) {
                Log.w("ThreeDSTest", "⚠️ No providers discovered! Check dependencies.")
            } else {
                Log.d("ThreeDSTest", "✅ Auto-discovery successful!")
            }

            Log.d("ThreeDSTest", "====================================")
        } catch (e: Exception) {
            Log.e("ThreeDSTest", "Error logging providers: ${e.message}", e)
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            statusText.text = message
            Log.d("ThreeDSTest", message)
        }
    }
}
