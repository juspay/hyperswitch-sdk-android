package io.hyperswitch.demo_app_lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.PaymentSession
import io.hyperswitch.authentication.AuthenticationSession
import io.hyperswitch.authentication.AuthenticationSessionLauncher
import io.hyperswitch.click_to_pay.ClickToPaySession
import io.hyperswitch.click_to_pay.models.CheckoutRequest
import io.hyperswitch.click_to_pay.models.CustomerPresenceRequest
import io.hyperswitch.click_to_pay.models.RecognizedCard
import io.hyperswitch.click_to_pay.models.StatusCode
import io.hyperswitch.paymentsession.PaymentMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class ClickToPayExample: Activity() {
    private var authenticationSession: AuthenticationSession? = null
    private var clickToPaySession: ClickToPaySession? = null
    private lateinit var resultText : TextView
    private var serverURL :String = "http://10.0.2.2:5252"
    private  var credentials : Credentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)
        resultText = findViewById(R.id.resultText)
        findViewById<View>(R.id.initialize).setOnClickListener {
            getAuthenticationCredentials()
        }
        findViewById<View>(R.id.initC2P).setOnClickListener {
            initiateClickToPaySession()
        }
        findViewById<View>(R.id.getUserType).setOnClickListener {
            getCustomerCards()
       }
    }
    data class Credentials(
        val clientSecret: String,
        val profileId: String,
        val authenticationId: String,
        val publishableKey: String,
        val merchantId : String
    )
    private fun getAuthenticationCredentials() {
        reset().get("$serverURL/create-c2p", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("BackendM", value.toString())
                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            credentials =  Credentials(
                                clientSecret = result.getString("clientSecret"),
                                profileId = result.getString("profileId"),
                                authenticationId = result.getString("authenticationId"),
                                publishableKey = result.getString("publishableKey"),
                                merchantId = result.getString("merchantId")
                            )
                            initializeHyperSwitch()
                        }else{
                            credentials = null
                        }
                    } catch (e: JSONException) {
                        Log.d("BackendM", e.toString())
                        credentials = null
                    }
                }

                override fun failure(error: FuelError) {
                    credentials = null
                    Log.d("Backend Response", error.message ?: "")
                }
            })
    }

    private fun initializeHyperSwitch(){
        CoroutineScope(Dispatchers.Main).launch {
            if (credentials != null) {
                // Initialize authentication session
                try {
                    authenticationSession = AuthenticationSession(
                        activity = this@ClickToPayExample,
                        publishableKey = credentials!!.publishableKey                    )
                    authenticationSession?.initAuthenticationSession(
                        clientSecret = credentials!!.clientSecret,
                        profileId = credentials!!.profileId,
                        authenticationId = credentials!!.authenticationId,
                        merchantId = credentials!!.merchantId
                    )
                }catch(e: Exception){
                    Log.d("C2P", e.message.toString())
                }

                // Initialize Click to Pay session
//                initiateClickToPaySession()
            } else {
                // Handle error
                showError("Failed to get authentication credentials")
            }

        }
    }
    private fun showError(message: String) {
        Toast.makeText(this, message,
            Toast.LENGTH_LONG).show()
    }
    private fun initiateClickToPaySession() {
        try {
            CoroutineScope(Dispatchers.Main).launch {

                clickToPaySession = authenticationSession?.initClickToPaySession()

                if (clickToPaySession != null) {
                    Log.d("HyperSwitch", "Click to Pay session initialized successfully")
                    checkCustomerPresence()
                } else {
                    showError("Failed to initialize Click to Pay session")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Error initiating Click to Pay: ${e.message}")
        }
    }

    private fun checkCustomerPresence() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Check with email

                val result = withContext(Dispatchers.IO) {
                    clickToPaySession?.isCustomerPresent(
                        CustomerPresenceRequest()
                    )
                }

                if (result?.customerPresent == true) {
                    Log.d("HyperSwitch", "Customer has a Click to Pay profile")
//                    getCustomerCards()
                } else {
                    Log.d("HyperSwitch", "Customer does not have a Click to Pay profile")
                    // Handle new customer flow
                    showMessage("No existing Click to Pay profile found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error checking customer presence: ${e.message}")
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun getCustomerCards() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val cardsStatus = withContext(Dispatchers.IO) {
                    clickToPaySession?.getUserType()
                }

                when (cardsStatus?.statusCode) {
                    StatusCode.RECOGNIZED_CARDS_PRESENT -> {
                        val cards = withContext(Dispatchers.IO) {
                            clickToPaySession?.getRecognizedCards()
                        }
                        if (cards != null) {
                            displayCards(cards)
                        }
                    }

                    StatusCode.TRIGGERED_CUSTOMER_AUTHENTICATION -> {
                        Log.d("HyperSwitch", "Customer authentication triggered")
                        // Show OTP input dialog
                        showOTPDialog()
                    }

                    StatusCode.NO_CARDS_PRESENT -> {
                        Log.d("HyperSwitch", "No cards found for this customer")
                        showMessage("No saved cards found")
                    }

                    else -> {
                        Log.d("HyperSwitch", "Unknown status: ${cardsStatus?.statusCode}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error retrieving cards: ${e.message}")
            }
        }
    }

    private fun displayCards(cards: List<RecognizedCard>) {
        // Display cards in RecyclerView or ListView
        Log.d("HyperSwitch", "Found ${cards.size} card(s)")

        cards.forEach { card ->
            Log.d("HyperSwitch", """
            Card: ${card.digitalCardData.descriptorName}
            Last 4 digits: ${card.panLastFour}
            Expiry: ${card.panExpirationMonth}/${card.panExpirationYear}
        """.trimIndent())
        }

        // Update UI with cards
        // setupCardsRecyclerView(cards)
    }
    private fun showOTPDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter OTP"

        builder.setTitle("Verify Your Identity")
            .setMessage("Please enter the OTP sent to your registered email/phone")
            .setView(input)
            .setPositiveButton("Verify") { dialog, _ ->
                val otpValue = input.text.toString()
                validateAuthentication(otpValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun validateAuthentication(otpValue: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val validatedCards = withContext(Dispatchers.IO) {
                    clickToPaySession?.validateCustomerAuthentication(otpValue)
                }

                if (validatedCards != null && validatedCards.isNotEmpty()) {
                    Log.d("HyperSwitch", "Authentication successful")
                    displayCards(validatedCards)
                } else {
                    showError("Authentication failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Authentication validation failed: ${e.message}")
            }
        }
    }



    @SuppressLint("ShowToast")
    private fun checkoutWithSelectedCard(card: RecognizedCard) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
//                showProgressDialog("Processing payment...")

                val checkoutRequest = CheckoutRequest(
                    srcDigitalCardId = card.srcDigitalCardId,
                    rememberMe = false,
//                    request3DSAuthentication = false
                )

                val checkoutResponse = withContext(Dispatchers.IO) {
                    clickToPaySession?.checkoutWithCard(checkoutRequest)
                }

//                hideProgressDialog()

                if (checkoutResponse?.status == "success") {
                    Log.d("HyperSwitch", "Checkout successful")
                    Log.d("HyperSwitch",
                        "Authentication ID: ${checkoutResponse.authenticationId}")

                    // Process payment on your backend
//                    processPayment(checkoutResponse)
                    resultText.setText("Success")
                //                    "Success",Toast.LENGTH_LONG)
                } else {
                    showError("Checkout failed")
                }
            } catch (e: Exception) {
//                hideProgressDialog()
                e.printStackTrace()
                showError("Error during checkout: ${e.message}")
            }
        }
    }




}