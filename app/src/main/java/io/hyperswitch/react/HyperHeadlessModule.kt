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
import com.hyperswitchtrident3ds.HyperswitchTrident3dsModule

class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct)
    //  HyperswitchTrident3dsModule.ActivityProvider 
     {

    override fun getName(): String {
        return "HyperHeadless"
    }

    // // Implementation of ActivityProvider interface
    // override fun getCurrentChallengeActivity(): android.app.Activity? {
    //     android.util.Log.d("HyperHeadless", "ActivityProvider.getCurrentChallengeActivity called - currentChallengeActivity: $currentChallengeActivity, rct.currentActivity: ${rct.currentActivity}")
    //     return currentChallengeActivity ?: rct.currentActivity
    // }

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
    fun getMessageVersion(callback: Callback) {
        android.util.Log.d("HyperHeadless", "getMessageVersion called - storing callback for manual execution")
        storedGetMessageVersionCallback = callback
        android.util.Log.d("HyperHeadless", "Callback stored successfully. Ready for manual execution.")
    }

    @ReactMethod
    fun getChallengeParams(params: ReadableMap, callback: Callback) {
        android.util.Log.d("HyperHeadless", "getChallengeParams called - storing callback and params for manual execution")
        
        storedGetChallengeParamsCallback = callback
        storedGetChallengeParamsData = params
        
        android.util.Log.d("HyperHeadless", "Challenge params callback stored successfully. Ready for manual execution.")
    }

    private var receiveChallengeCallback: ((String) -> Unit)? = null
    private var doChallengeCallback: ((String) -> Unit)? = null
    
    private var currentChallengeActivity: android.app.Activity? = null
    
    private var storedGetMessageVersionCallback: Callback? = null
    private var storedGetChallengeParamsCallback: Callback? = null
    private var storedGetChallengeParamsData: ReadableMap? = null
    private var storedChallengeParameters: WritableMap? = null

    @ReactMethod
    fun sendMessageToNative(message: String) {
        android.util.Log.d("HyperHeadless", "Received message from React Native: $message")
        
        try {
            val jsonObject = org.json.JSONObject(message)
            
            when {
                jsonObject.has("challengeResult") -> {
                    receiveChallengeCallback?.invoke(message)
                    receiveChallengeCallback = null
                }
                jsonObject.has("doChallengeResult") -> {
                    doChallengeCallback?.invoke(message)
                    doChallengeCallback = null
                }
            }
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
        android.util.Log.d("HyperHeadless", "Stored currentChallengeActivity: $currentChallengeActivity")

        val callback = storedGetChallengeParamsCallback
        
        if (callback != null) {
            android.util.Log.d("HyperHeadless", "Triggering stored receiveChallengeParamsCallback with challenge parameters")
            
            try {
                // Call the stored receiveChallengeParamsCallback with challenge parameters
                callback.invoke(storedChallengeParameters!!)
                
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
     * Execute the stored getMessageVersion callback manually
     * This triggers the ReScript generateAReqParamsCallback
     */
    fun executeStoredGetMessageVersion(): Boolean {
        android.util.Log.d("HyperHeadless", "executeStoredGetMessageVersion called")
        
        return storedGetMessageVersionCallback?.let { callback ->
            android.util.Log.d("HyperHeadless", "Executing stored getMessageVersion callback")
            
            val params = Arguments.createMap()
            params.putString("messageVersion", "2.3.1")
            params.putString("directoryServerId", "A000000004") 
            params.putString("cardNetwork", "VISA")
            
            callback.invoke(params)
            
            storedGetMessageVersionCallback = null
            
            android.util.Log.d("HyperHeadless", "getMessageVersion callback executed successfully")
            true
        } ?: run {
            android.util.Log.w("HyperHeadless", "No stored getMessageVersion callback found")
            false
        }
    }
    
    
    /**
     * Check if there are stored callbacks ready for execution
     */
    fun hasStoredGetMessageVersionCallback(): Boolean = storedGetMessageVersionCallback != null
    
    fun hasStoredGetChallengeParamsCallback(): Boolean = 
        storedGetChallengeParamsCallback != null && storedGetChallengeParamsData != null
    
    /**
     * Get the stored challenge params data (which contains aReqParams from generateAReqParamsCallback)
     * This is used by Transaction.getAuthenticationRequestParameters() to extract aReqParams
     */
    fun getStoredChallengeParamsData(): ReadableMap? {
        android.util.Log.d("HyperHeadless", "getStoredChallengeParamsData called - returning aReqParams data: ${storedGetChallengeParamsData != null}")
        return storedGetChallengeParamsData
    }
    
    /**
     * Clear all stored callbacks (useful for cleanup)
     */
    fun clearStoredCallbacks() {
        android.util.Log.d("HyperHeadless", "Clearing all stored callbacks")
        storedGetMessageVersionCallback = null
        storedGetChallengeParamsCallback = null
        storedGetChallengeParamsData = null
        storedChallengeParameters = null
    }

    // Companion object to provide static access to the module instance
    companion object {
        private var instance: HyperHeadlessModule? = null
        
        fun getInstance(): HyperHeadlessModule? = instance
        
        internal fun setInstance(module: HyperHeadlessModule) {
            instance = module
        }
    }
    
    init {
        setInstance(this)
        // Set this module as the activity provider for the Trident 3DS module
        // HyperswitchTrident3dsModule.setActivityProvider(this)
        android.util.Log.d("HyperHeadless", "Activity provider set for Trident 3DS module")
    }
}
