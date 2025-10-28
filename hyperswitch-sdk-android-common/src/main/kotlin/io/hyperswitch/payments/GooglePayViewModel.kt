package io.hyperswitch.payments

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONObject

class GooglePayViewModel(private val context: Context) {

    private lateinit var paymentsClient: PaymentsClient

    fun fetchCanUseGooglePay(isReadyToPayJson: JSONObject, environment: String): Boolean {

        paymentsClient = createPaymentsClient(context, environment)
        var isAvailable = false;
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString())
        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                isAvailable = completedTask.getResult(ApiException::class.java) ?: false
                Log.d(
                    "GPAY",
                    "GPAY CAN BE USED ${completedTask.getResult(ApiException::class.java)}"
                )
            } catch (exception: ApiException) {
                isAvailable = false
                Log.w("GPAY WARNING", exception)
            }
        }
        return isAvailable
    }

    fun getLoadPaymentDataTask(paymentDataRequestJson: JSONObject): Task<PaymentData> {
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())
        return paymentsClient.loadPaymentData(request)
    }

    private fun createPaymentsClient(context: Context, environment: String): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(if (environment == "TEST") WalletConstants.ENVIRONMENT_TEST else WalletConstants.ENVIRONMENT_PRODUCTION)
            .build()

        return Wallet.getPaymentsClient(context, walletOptions)
    }

}