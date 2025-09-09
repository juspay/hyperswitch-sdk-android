package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.payments.paymentlauncher.PaymentResult
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.isPresented
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.paymentIntentClientSecret
import io.hyperswitch.paymentsession.ExitHeadlessCallBackManager
import io.hyperswitch.paymentsession.GetPaymentSessionCallBackManager
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentMethod
import io.hyperswitch.paymentsession.PaymentSessionHandler
class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct)
    //  HyperswitchTrident3dsModule.ActivityProvider 
     {

    override fun getName(): String {
        return "HyperHeadless"
    }

    /**
     * Get the current activity for 3DS operations
     * This method can be called by any 3DS SDK via reflection
     */
    fun getCurrentActivityFor3DS(): android.app.Activity? {
        android.util.Log.d("HyperHeadless", "getCurrentActivityFor3DS called - delegating to static getChallengeActivity()")
        return getChallengeActivity()
    }

    // Method to initialise the payment session
    @ReactMethod
    fun initialisePaymentSession(callback: Callback) {
        if (GetPaymentSessionCallBackManager.getCallback() != null || !isPresented) {
            callback.invoke(
                Arguments.fromBundle(
                    LaunchOptions().getBundle(
                        rct,
                        paymentIntentClientSecret ?: ""
                    ).getBundle("props")
                )
            )
        }
    }

    // Method to initialise the authentication session
    @ReactMethod
    fun initialiseAuthSession(callback: Callback) {
        val clientSecret = io.hyperswitch.authentication.AuthenticationSession.paymentIntentClientSecret
        if (clientSecret != null) {
            callback.invoke(
                Arguments.fromBundle(
                    LaunchOptions().getAuthenticationBundle(
                        rct,
                        clientSecret
                    ).getBundle("props")
                )
            )
        } else {
            val errorBundle = Arguments.createMap()
            errorBundle.putString("error", "No authentication session initialized")
            callback.invoke(errorBundle)
        }
    }

    // Method to get the payment session
    @ReactMethod
    fun getPaymentSession(
        getPaymentMethodData: ReadableMap,
        getPaymentMethodData2: ReadableMap,
        getPaymentMethodDataArray: ReadableArray,
        callback: Callback
    ) {
        val handler = object : PaymentSessionHandler {
            override fun getCustomerDefaultSavedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData)
            }

            override fun getCustomerLastUsedPaymentMethodData(): PaymentMethod {
                return parseGetPaymentMethodData(getPaymentMethodData2)
            }

            override fun getCustomerSavedPaymentMethodData(): Array<PaymentMethod> {
                val array = mutableListOf<PaymentMethod>()
                for (i in 0 until getPaymentMethodDataArray.size()) {
                    getPaymentMethodDataArray.getMap(i)?.let {
                        array.add(parseGetPaymentMethodData(it))
                    }
                }
                return array.toTypedArray()
            }

            override fun confirmWithCustomerDefaultPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData.getMap("_0")?.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerLastUsedPaymentMethod(
                cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                getPaymentMethodData2.getMap("_0")?.getString("payment_token")
                    ?.let { confirmWithCustomerPaymentToken(it, cvc, resultHandler) }
            }

            override fun confirmWithCustomerPaymentToken(
                paymentToken: String, cvc: String?, resultHandler: (PaymentResult) -> Unit
            ) {
                try {
                    ExitHeadlessCallBackManager.setCallback(resultHandler)
                    val map = Arguments.createMap()
                    map.putString("paymentToken", paymentToken)
                    map.putString("cvc", cvc)
                    callback.invoke(map)
                } catch (ex: Exception) {
                    val throwable = Throwable("Not Initialised")
                    throwable.initCause(Throwable("Not Initialised"))
                    resultHandler(PaymentResult.Failed(throwable))
                }
            }
        }
        GetPaymentSessionCallBackManager.executeCallback(handler)
    }

    private fun parseGetPaymentMethodData(readableMap: ReadableMap): PaymentMethod {

        val tag = try {
            readableMap.getString("TAG")
        } catch (ex: Exception) {
            ""
        }
        val dataObject: ReadableMap = readableMap.getMap("_0") ?: Arguments.createMap()

        return when (tag) {
            "SAVEDLISTCARD" -> {
                PaymentMethod.Card(
                    isDefaultPaymentMethod = dataObject.getBoolean("isDefaultPaymentMethod"),
                    paymentToken = dataObject.getString("payment_token") ?: "",
                    cardScheme = dataObject.getString("cardScheme") ?: "",
                    name = dataObject.getString("name") ?: "",
                    expiryDate = dataObject.getString("expiry_date") ?: "",
                    cardNumber = dataObject.getString("cardNumber") ?: "",
                    nickName = dataObject.getString("nick_name") ?: "",
                    cardHolderName = dataObject.getString("cardHolderName") ?: "",
                    requiresCVV = dataObject.getBoolean("requiresCVV"),
                    created = dataObject.getString("created") ?: "",
                    lastUsedAt = dataObject.getString("lastUsedAt") ?: "",
                )
            }

            "SAVEDLISTWALLET" -> {
                PaymentMethod.Wallet(
                    isDefaultPaymentMethod = dataObject.getBoolean("isDefaultPaymentMethod"),
                    paymentToken = dataObject.getString("payment_token") ?: "",
                    walletType = dataObject.getString("walletType") ?: "",
                    created = dataObject.getString("created") ?: "",
                    lastUsedAt = dataObject.getString("lastUsedAt") ?: "",
                )
            }

            else -> {
                PaymentMethod.Error(
                    code = readableMap.getString("code") ?: "",
                    message = readableMap.getString("message") ?: ""
                )
            }
        }
    }


    // Method to exit the headless mode
    @ReactMethod
    fun exitHeadless(status: String) {
        ExitHeadlessCallBackManager.executeCallback(status)
        // reactInstanceManager?.currentReactContext?.destroy()
        // reactInstanceManager?.destroy()
    }

    @ReactMethod
    fun getAuthRequestParams(callback: Callback) {
        android.util.Log.d("HyperHeadless", "getAuthRequestParams called - storing callback for manual execution")
        storedGetAuthRequestParamsCallback = callback
        android.util.Log.d("HyperHeadless", "Callback stored successfully. Ready for manual execution.")
    }

    @ReactMethod
    fun sendAReqAndReceiveChallengeParams(params: ReadableMap, callback: Callback) {
        android.util.Log.d("HyperHeadless", "sendAReqAndReceiveChallengeParams called - storing callback and params")
        android.util.Log.d("HyperHeadless", "Received params: ${params.toString()}")
        
        storedsendAReqAndReceiveChallengeParamsCallback = callback
        storedsendAReqAndReceiveChallengeParamsData = params
        
        android.util.Log.d("HyperHeadless", "Challenge params callback stored successfully. Data available for callback mechanism.")
        android.util.Log.d("HyperHeadless", "Stored data status: ${params.getString("status")}")
        
        // Trigger any waiting auth parameters callback immediately when data arrives
        authParametersCallback?.let { authCallback ->
            android.util.Log.d("HyperHeadless", "Triggering auth parameters callback - data has arrived!")
            authCallback(params)
            authParametersCallback = null // Clear the callback after use
        }
    }

    private var receiveChallengeCallback: ((String) -> Unit)? = null
    private var doChallengeCallback: ((String) -> Unit)? = null
    
    private var currentChallengeActivity: android.app.Activity? = null
    
    private var storedGetAuthRequestParamsCallback: Callback? = null
    private var storedsendAReqAndReceiveChallengeParamsCallback: Callback? = null
    private var storedsendAReqAndReceiveChallengeParamsData: ReadableMap? = null
    private var storedChallengeParameters: WritableMap? = null
    

    private var authParametersCallback: ((com.facebook.react.bridge.ReadableMap?) -> Unit)? = null

    private var initAuthenticationSessionCallback: ((io.hyperswitch.authentication.AuthenticationResult) -> Unit)? = null

    @ReactMethod
    fun sendMessageToNative(message: String) {
        android.util.Log.d("HyperHeadless", "Received message from React Native: $message")
        
        try {
            val jsonObject = org.json.JSONObject(message)
            
            if (jsonObject.has("method")) {
                val method = jsonObject.getString("method")
                val status = jsonObject.optBoolean("status", false)
                
                android.util.Log.d("HyperHeadless", "Processing method-based message: method=$method, status=$status")
                
                if (!status) {
                    val errorObject = jsonObject.optJSONObject("error")
                    val errorMessage = errorObject?.optString("message") ?: "Unknown error"
                    
                    when (method) {
                        "initialiseSdkFunc" -> {
                            android.util.Log.d("HyperHeadless", "Routing initialiseSdkFunc error to initAuthenticationSession callback")
                            initAuthenticationSessionCallback?.let { callback ->
                                callback(io.hyperswitch.authentication.AuthenticationResult.Error(errorMessage))
                                initAuthenticationSessionCallback = null
                            } ?: run {
                                android.util.Log.w("HyperHeadless", "No initAuthenticationSession callback registered for error: $errorMessage")
                            }
                        }
                        
                        "generateAReqParams" -> {
                            android.util.Log.d("HyperHeadless", "Routing generateAReqParams error to callback mechanism")
                            authParametersCallback?.let { callback ->
                                android.util.Log.d("HyperHeadless", "Triggering auth parameters callback with null due to error")
                                callback(null)
                                authParametersCallback = null
                            }
                        }
                        
                        "generateChallenge" -> {
                            android.util.Log.d("HyperHeadless", "Routing generateChallenge error to doChallenge callback")
                            doChallengeCallback?.let { callback ->
                                val errorResponse = "{\"status\":\"error\",\"message\":\"$errorMessage\"}"
                                callback(errorResponse)
                                doChallengeCallback = null
                            } ?: run {
                                android.util.Log.w("HyperHeadless", "No doChallenge callback registered for error: $errorMessage")
                            }
                        }
                        
                        else -> {
                            android.util.Log.w("HyperHeadless", "Unknown method in error message: $method")
                        }
                    }
                } else {
                    // Handle success messages
                    when (method) {
                        "generateChallenge" -> {
                            android.util.Log.d("HyperHeadless", "Routing generateChallenge success to doChallenge callback")
                            doChallengeCallback?.let { callback ->
                                android.util.Log.d("HyperHeadless", "Forwarding challenge success message to Transaction.kt")
                                callback(message)
                                doChallengeCallback = null
                            } ?: run {
                                android.util.Log.w("HyperHeadless", "No doChallenge callback registered for success message")
                            }
                        }
                        
                        else -> {
                            android.util.Log.d("HyperHeadless", "Received success message for method: $method")
                        }
                    }
                }
                
                return 
            }
            
            // when {
            //     jsonObject.has("challengeResult") -> {
            //         receiveChallengeCallback?.invoke(message)
            //         receiveChallengeCallback = null
            //     }
            //     jsonObject.has("doChallengeResult") -> {
            //         doChallengeCallback?.invoke(message)
            //         doChallengeCallback = null
            //     }
            // }
        } catch (e: Exception) {
            android.util.Log.e("HyperHeadless", "Error parsing response: ${e.message}")
        }
    }

    fun initializeSDK(publishableKey: String) {
        try {
            android.util.Log.d("HyperHeadless", "Initializing PaymentConfiguration with publishableKey: $publishableKey")
            PaymentConfiguration.init(rct.applicationContext, publishableKey)
            android.util.Log.d("HyperHeadless", "PaymentConfiguration initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("HyperHeadless", "Error initializing PaymentConfiguration: ${e.message}")
        }
    }
    

    fun receiveChallengeParams(
        acsSignedContent: String,
        acsTransactionId: String,
        acsRefNumber: String,
        threeDSServerTransId: String,
        threeDSRequestorAppURL: String?,
        callback: ((String) -> Unit)? = null
    ) {
        android.util.Log.d("HyperHeadless", "receiveChallengeParams called - storing challenge parameters for doChallenge")
        
        receiveChallengeCallback = callback
        
        val challengeParams = Arguments.createMap()
        challengeParams.putString("acsSignedContent", acsSignedContent)
        challengeParams.putString("acsTransactionId", acsTransactionId)
        challengeParams.putString("acsRefNumber", acsRefNumber)
        challengeParams.putString("threeDSServerTransId", threeDSServerTransId)
        challengeParams.putString("threeDSRequestorAppURL", threeDSRequestorAppURL)
        
        storedChallengeParameters = challengeParams
        
        android.util.Log.d("HyperHeadless", "Challenge parameters stored successfully")
        
        callback?.invoke("{\"status\":\"success\",\"message\":\"Challenge parameters received\"}")
    }

    fun doChallenge(activity: android.app.Activity? = null, callback: ((String) -> Unit)? = null) {
        android.util.Log.d("HyperHeadless", "doChallenge called with activity: $activity")
        
        if (storedChallengeParameters == null) {
            android.util.Log.e("HyperHeadless", "No stored challenge parameters found")
            callback?.invoke("{\"status\":\"error\",\"message\":\"No challenge parameters available\"}")
            return
        }
        
        doChallengeCallback = callback
        
        currentChallengeActivity = activity
        setChallengeActivity(activity)
        
        if (activity != null) {
            io.hyperswitch.authentication.AuthActivityManager.setActivity(activity)
            android.util.Log.d("HyperHeadless", "Activity set in AuthActivityManager: $activity")
        }

        val storedCallback = storedsendAReqAndReceiveChallengeParamsCallback
        
        if (storedCallback != null) {
            android.util.Log.d("HyperHeadless", "Triggering stored receiveChallengeParamsCallback with challenge parameters")
            
            try {
                storedCallback.invoke(storedChallengeParameters!!)
                
                android.util.Log.d("HyperHeadless", "receiveChallengeParamsCallback triggered successfully")
            } catch (e: Exception) {
                android.util.Log.e("HyperHeadless", "Error triggering receiveChallengeParamsCallback: ${e.message}")
                callback?.invoke("{\"status\":\"error\",\"message\":\"Failed to trigger challenge flow: ${e.message}\"}")
            }
        } else {
            android.util.Log.e("HyperHeadless", "No stored receiveChallengeParamsCallback found")
            callback?.invoke("{\"status\":\"error\",\"message\":\"No receiveChallengeParamsCallback available\"}")
        }
    }
    
    
    /**
     * Execute the stored getAuthRequestParams callback manually
     * This triggers the ReScript generateAReqParamsCallback
     */
    fun executeStoredGetAuthRequestParams(): Boolean {
        android.util.Log.d("HyperHeadless", "executeStoredGetAuthRequestParams called")
        
        return storedGetAuthRequestParamsCallback?.let { callback ->
            android.util.Log.d("HyperHeadless", "Executing stored getAuthRequestParams callback")
            
            val params = Arguments.createMap()
            params.putString("messageVersion", "2.2.0")
            params.putString("directoryServerId", "A000000004") 
            params.putString("cardNetwork", "VISA")
            
            callback.invoke(params)
            
            storedGetAuthRequestParamsCallback = null
            
            android.util.Log.d("HyperHeadless", "getAuthRequestParams callback executed successfully")
            true
        } ?: run {
            android.util.Log.w("HyperHeadless", "No stored getAuthRequestParams callback found")
            false
        }
    }
    
    
    /**
     * Check if there are stored callbacks ready for execution
     */
    fun hasStoredGetAuthRequestParamsCallback(): Boolean = storedGetAuthRequestParamsCallback != null
    
    fun hasStoredsendAReqAndReceiveChallengeParamsCallback(): Boolean = 
        storedsendAReqAndReceiveChallengeParamsCallback != null && storedsendAReqAndReceiveChallengeParamsData != null
    
    /**
     * Get the stored challenge params data (which contains aReqParams from generateAReqParamsCallback)
     * This is used by Transaction.getAuthenticationRequestParameters() to extract aReqParams
     */
    fun getStoredChallengeParamsData(): ReadableMap? {
        android.util.Log.d("HyperHeadless", "getStoredChallengeParamsData called - returning aReqParams data: ${storedsendAReqAndReceiveChallengeParamsData != null}")
        return storedsendAReqAndReceiveChallengeParamsData
    }
    


    /**
     * Set the callback for auth parameters (replaces CountDownLatch approach)
     * This is called by Transaction to register a callback that will be triggered when data arrives
     */
    fun setAuthParametersCallback(callback: (com.facebook.react.bridge.ReadableMap?) -> Unit) {
        android.util.Log.d("HyperHeadless", "Setting auth parameters callback")
        authParametersCallback = callback
    }

    /**
     * Set the init authentication session callback for error propagation
     * This is called by AuthenticationSession to register its callback for error handling
     */
    fun setInitAuthenticationSessionCallback(callback: (io.hyperswitch.authentication.AuthenticationResult) -> Unit) {
        android.util.Log.d("HyperHeadless", "Setting init authentication session callback for error propagation")
        initAuthenticationSessionCallback = callback
    }

    /**
     * Clear all stored callbacks (useful for cleanup)
     */
    fun clearStoredCallbacks() {
        android.util.Log.d("HyperHeadless", "Clearing all stored callbacks")
        storedGetAuthRequestParamsCallback = null
        storedsendAReqAndReceiveChallengeParamsCallback = null
        storedsendAReqAndReceiveChallengeParamsData = null
        storedChallengeParameters = null
        authParametersCallback = null
        initAuthenticationSessionCallback = null
    }

    companion object {
        private var instance: HyperHeadlessModule? = null
        
        @Volatile
        private var staticChallengeActivity: android.app.Activity? = null
        
        fun getInstance(): HyperHeadlessModule? = instance
        
        internal fun setInstance(module: HyperHeadlessModule) {
            instance = module
        }
        
        /**
         * Static setter for challenge activity - can be called from anywhere
         */
        @JvmStatic
        fun setChallengeActivity(activity: android.app.Activity?) {
            android.util.Log.d("HyperHeadless", "Static setChallengeActivity called with: $activity")
            staticChallengeActivity = activity
        }
        
        /**
         * Static getter for challenge activity - used by 3DS modules via reflection
         */
        @JvmStatic
        fun getChallengeActivity(): android.app.Activity? {
            val activity = staticChallengeActivity ?: instance?.rct?.currentActivity
            android.util.Log.d("HyperHeadless", "Static getChallengeActivity returning: $activity (static: $staticChallengeActivity, rct: ${instance?.rct?.currentActivity})")
            return activity
        }
        
        /**
         * Clear the static activity reference
         */
        @JvmStatic
        fun clearChallengeActivity() {
            android.util.Log.d("HyperHeadless", "Static clearChallengeActivity called")
            staticChallengeActivity = null
        }
    }
    
    init {
        setInstance(this)
        android.util.Log.d("HyperHeadless", "HyperHeadlessModule initialized with direct activity management")
    }
}
