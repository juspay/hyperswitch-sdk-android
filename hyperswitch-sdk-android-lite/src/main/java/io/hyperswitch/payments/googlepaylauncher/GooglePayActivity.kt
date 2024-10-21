package io.hyperswitch.payments.googlepaylauncher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData

import org.json.JSONException
import org.json.JSONObject

class GooglePayActivity : AppCompatActivity() {

    private val gPayRequestCode = 1212
    private val model: GooglePayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val gPayRequest = JSONObject(intent.getStringExtra("googlePayRequest").toString())
        var isReadyToPayJson: JSONObject? = null
        var environment = "TEST" // Default Value is TEST in capitals
        if (gPayRequest.has("paymentDataRequest") and gPayRequest.has("environment")) {
            isReadyToPayJson = gPayRequest.getJSONObject("paymentDataRequest")
            environment = gPayRequest.getString("environment").uppercase()
        }

        if (isReadyToPayJson != null) {
            model.fetchCanUseGooglePay(isReadyToPayJson, environment)
        }

        if (gPayRequest.has("paymentDataRequest")) {
            requestPayment(gPayRequest.getJSONObject("paymentDataRequest"))
        } else {
            Log.e("GooglePay", "GPay PaymentRequest Not available")
        }
    }


    private fun requestPayment(paymentDataRequestJson: JSONObject) {
        val task = model.getLoadPaymentDataTask(paymentDataRequestJson)

        // Calling GPay UI for Payment with gPayRequestCode for onActivityResult
        AutoResolveHelper.resolveTask(task, this, gPayRequestCode)
    }

    private fun handlePaymentSuccess(paymentData: PaymentData) {
        GooglePayCallbackManager.executeCallback(mutableMapOf<String, String?>().apply {
            try {
                put("paymentMethodData", JSONObject(paymentData.toJson()).toString())
            } catch (error: JSONException) {
                put("error", error.message)
            }
        })
        finish()
    }

    private fun handleError(message: String) {
        GooglePayCallbackManager.executeCallback(mutableMapOf<String, String?>().apply {
            put("error", message)
        })
        finish()
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == gPayRequestCode) {
            when (resultCode) {
                RESULT_OK -> data?.let { intent ->
                    PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
                }

                RESULT_CANCELED -> handleError("Cancel")
                else -> handleError("Failure")
            }
        }
    }
}
