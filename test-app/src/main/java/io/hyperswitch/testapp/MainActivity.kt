package io.hyperswitch.testapp

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.PaymentSession
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import io.hyperswitch.testapp.JsonToConfigurationConverter.createConfigurationFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import io.hyperswitch.lite.PaymentSession as PaymentSessionLite

class MainActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
    }

    lateinit var ctx: Activity;
    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""
    private var serverUrl = DEFAULT_SERVER_URL
    private lateinit var paymentSession: PaymentSession
    private lateinit var paymentSessionLite: PaymentSessionLite
    private lateinit var editText : EditText
    private lateinit var statusText: TextView

    private fun getSharedPreferences(): android.content.SharedPreferences {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences().edit().putString(KEY_SERVER_URL, url).apply()
    }

    private fun loadServerUrl(): String {
        return getSharedPreferences().getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    private fun updateServerUrl(newUrl: String) {
        if (isValidUrl(newUrl)) {
            serverUrl = newUrl
            saveServerUrl(newUrl)
        } else {
            setStatus("Invalid URL format")
        }
    }

    private fun jsonToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            
            when (value) {
                is JSONObject -> map[key] = jsonToMap(value)
                is JSONArray -> map[key] = jsonArrayToList(value)
                else -> map[key] = value
            }
        }
        
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            
            when (value) {
                is JSONObject -> list.add(jsonToMap(value))
                is JSONArray -> list.add(jsonArrayToList(value))
                else -> list.add(value)
            }
        }
        
        return list
    }

    private suspend fun fetchSDKpropsString() : String? =suspendCancellableCoroutine { continuation ->
        reset().get("${serverUrl}/get-sdk-props?val=100")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val jsonObject = JSONObject(value ?: "{}")
                        val properties = jsonObject.getJSONObject("sdkProps").toString()
                        continuation.resume(properties)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun failure(error: FuelError) {
                    continuation.resumeWithException(error)
                }
            })
    }

    private suspend fun fetchSDKProperties(): Map<String, Any> =
        suspendCancellableCoroutine { continuation ->
            reset().get("${serverUrl}/get-sdk-props")
                .responseString(object : Handler<String?> {
                    override fun success(value: String?) {
                        try {
                            val jsonObject = JSONObject(value ?: "{}")
                            val properties = jsonToMap(jsonObject.getJSONObject("sdkProps"))
                            continuation.resume(properties)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun failure(error: FuelError) {
                        continuation.resumeWithException(error)
                    }
                })
        }

    private suspend fun getCustomisations(): PaymentSheet.Configuration {
        return try {
            val props = fetchSDKpropsString()
            props.let {
                if (it != null) {
                    createConfigurationFromJson(it)
                }else{
                    PaymentSheet.Configuration.Builder("Example").build()
                }
            }
        } catch (e: Exception) {
            Log.e("SDK Properties", "Error fetching properties: ${e.message}")
            PaymentSheet.Configuration.Builder("Example 3").build()
        }
    }

    private suspend fun fetchNetceteraApiKey(): String? =
        suspendCancellableCoroutine { continuation ->
            reset().get("${editText.text}/netcetera-sdk-api-key")
                .responseString(object : Handler<String?> {
                    override fun success(value: String?) {
                        try {
                            val result = value?.let { JSONObject(it) }
                            val netceteraApiKey = result?.getString("netceteraApiKey")
                            continuation.resume(netceteraApiKey)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun failure(error: FuelError) {
                        continuation.resumeWithException(error)
                    }
                })
        }

    private fun getCL() {

        ctx.findViewById<View>(R.id.launchButton).isEnabled = false;
        ctx.findViewById<View>(R.id.launchWebButton).isEnabled = false;
        ctx.findViewById<View>(R.id.confirmButton).isEnabled = false;

        reset().get("${editText.text}/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            paymentIntentClientSecret = result.getString("clientSecret")
                            publishKey = result.getString("publishableKey")

                            /**
                             *
                             * Create Payment Session Object
                             *
                             * */

                            paymentSession = PaymentSession(ctx, publishKey)
                            paymentSessionLite = PaymentSessionLite(ctx, publishKey)

                            /**
                             *
                             * Initialise Payment Session
                             *
                             * */

                            paymentSession.initPaymentSession(paymentIntentClientSecret)
                            paymentSessionLite.initPaymentSession(paymentIntentClientSecret)

                            paymentSession.getCustomerSavedPaymentMethods { it ->

                                val text =
                                    when (val data = it.getCustomerLastUsedPaymentMethodData()) {
                                        is PaymentMethod.Card -> arrayOf(
                                            data.cardScheme + " - " + data.cardNumber,
                                            true
                                        )

                                        is PaymentMethod.Wallet -> arrayOf(data.walletType, true)
                                        is PaymentMethod.Error -> arrayOf(data.message, false)
                                    }

                                setStatus("Last Used PM: " + text[0])

                                ctx.runOnUiThread {
                                    ctx.findViewById<View>(R.id.confirmButton).isEnabled = true
                                    ctx.findViewById<View>(R.id.confirmButton)
                                        .setOnClickListener { _ ->
                                            it.confirmWithCustomerLastUsedPaymentMethod {
                                                onPaymentResult(it)
                                            }
                                        }
                                }
                            }

                            ctx.runOnUiThread {
                                ctx.findViewById<View>(R.id.launchButton).isEnabled = true
                                ctx.findViewById<View>(R.id.launchWebButton).isEnabled = true
                            }
                        }
                    } catch (e: JSONException) {
                        Log.d("Backend Response", e.toString())
                        setStatus("could not connect to the server")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.d("Backend Response", error.toString())
                    setStatus("could not connect to the server")
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ctx = this
        editText = ctx.findViewById<EditText>(R.id.ipAddressInput)
        statusText = ctx.findViewById<TextView>(R.id.resultText)
        
        // Load saved URL or use default
        serverUrl = loadServerUrl()
        editText.setText(serverUrl)

        // Add TextWatcher for URL validation
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { newUrl ->
                    if (newUrl.isNotEmpty()) {
                        updateServerUrl(newUrl)
                    }
                }
            }
        })

        /**
         *
         * Merchant API call to get Client Secret
         *
         * */

        getCL()



        findViewById<View>(R.id.reloadButton).setOnClickListener { getCL() }

        /**
         *
         * Launch Payment Sheet
         *
         * */

        findViewById<View>(R.id.launchButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val customisations = getCustomisations()
                Log.i("data", customisations.toString() )
                paymentSession.presentPaymentSheet(customisations,::onPaymentSheetResult)



            }
        }

        findViewById<View>(R.id.launchWebButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                paymentSessionLite.presentPaymentSheet(
                    getCustomisations(),
                    ::onPaymentSheetResult
                )
            }
        }

    }

    private fun setStatus(error: String) {
        runOnUiThread {
            statusText.text = error
        }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                setStatus(paymentSheetResult.data)
            }

            is PaymentSheetResult.Failed -> {
                setStatus(paymentSheetResult.error.message ?: "")
            }

            is PaymentSheetResult.Completed -> {
                setStatus(paymentSheetResult.data)
            }
        }
    }

    private fun onPaymentResult(paymentResult: PaymentResult) {
        when (paymentResult) {
            is PaymentResult.Canceled -> {
                setStatus(paymentResult.data)
            }

            is PaymentResult.Failed -> {
                setStatus(paymentResult.throwable.message ?: "")
            }

            is PaymentResult.Completed -> {
                setStatus(paymentResult.data)
            }
        }
    }
}