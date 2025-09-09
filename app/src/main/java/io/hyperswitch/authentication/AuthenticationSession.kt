package io.hyperswitch.authentication

import android.app.Activity
import android.os.Bundle
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.ReactNativeUtils
import io.hyperswitch.paymentsession.SDKInterface
import io.hyperswitch.react.HyperHeadlessModule

class AuthenticationSession(
    private val activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
    private val customParams: Bundle? = null,
    private val reactNativeUtils: SDKInterface = ReactNativeUtils(activity)
) {
    private var currentSession: Session? = null
    private val launchOptions = LaunchOptions(activity)
    
    companion object {
        var isPresented: Boolean = false
        var paymentIntentClientSecret: String? = null
    }

    init {
        // Initialize PaymentConfiguration if not already done
        if (publishableKey.isNotEmpty()) {
            try {
                PaymentConfiguration.init(activity.applicationContext, publishableKey, customBackendUrl, customLogUrl, "",customParams)
            } catch (e: Exception) {
                // PaymentConfiguration might already be initialized, which is fine
            }
        }
        
        // Initialize React Native instance using the proven pattern
        reactNativeUtils.initializeReactNativeInstance()
    }

    /**
     * Initialize 3DS Session
     * @param authIntentClientSecret The client secret for the authentication intent
     * @param configuration Configuration object containing 3DS SDK settings
     * @param callback Callback to handle initialization result
     * @return Session object for further operations
     */
    fun initThreeDsSession(
        authIntentClientSecret: String,
        configuration: AuthenticationConfiguration,
        callback: (AuthenticationResult) -> Unit
    ): Session {
        try {
            AuthenticationSession.paymentIntentClientSecret = authIntentClientSecret
            isPresented = false
            
            // val bundle = launchOptions.getAuthenticationBundle(authIntentClientSecret)
            
            reactNativeUtils.recreateReactContext()
            
            waitForReactNativeContext { hyperHeadlessModule ->
                if (hyperHeadlessModule != null) {
                    hyperHeadlessModule.setInitAuthenticationSessionCallback(callback)
                    android.util.Log.d("AuthenticationSession", "Registered callback with HyperHeadlessModule for error propagation")
                    
                    currentSession?.let { session ->
                        try {
                            val field = session.javaClass.getDeclaredField("hyperHeadlessModule")
                            field.isAccessible = true
                            field.set(session, hyperHeadlessModule)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthenticationSession", "Could not update hyperHeadlessModule: ${e.message}")
                        }
                    }
                    callback(AuthenticationResult.Success("Authentication session initialized successfully"))
                } else {
                    callback(AuthenticationResult.Error("React Native context not available"))
                }
            }
            
            currentSession = Session(
                activity = activity,
                publishableKey = publishableKey,
                paymentIntentClientSecret = authIntentClientSecret,
                uiCustomization = configuration.uiCustomization,
                hyperHeadlessModule = null
            )

            return currentSession!!
            
        } catch (e: Exception) {
            val error = AuthenticationResult.Error("Failed to initialize: ${e.message}")
            callback(error)
            throw e
        }
    }

    /**
     * Wait for React Native context to be ready
     */
    private fun waitForReactNativeContext(callback: (HyperHeadlessModule?) -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var retryCount = 0
        val maxRetries = 20
        val retryDelayMs = 250L

        fun checkForModule() {
            if (activity.isFinishing || activity.isDestroyed) {
                android.util.Log.e("AuthenticationSession", "Activity is no longer available during React Native context wait")
                callback(null)
                return
            }
            
            val hyperHeadlessModule = io.hyperswitch.react.HyperHeadlessModule.getInstance()
            if (hyperHeadlessModule != null) {
                android.util.Log.d("AuthenticationSession", "React Native context ready after $retryCount retries")
                
                // Set activity in both the old and new systems for compatibility
                io.hyperswitch.react.HyperHeadlessModule.setChallengeActivity(activity)
                io.hyperswitch.authentication.AuthActivityManager.setActivity(activity)
                android.util.Log.d("AuthenticationSession", "Activity set in both HyperHeadlessModule and AuthActivityManager: $activity")
                
                callback(hyperHeadlessModule)
            } else if (retryCount < maxRetries) {
                retryCount++
                android.util.Log.d("AuthenticationSession", "Waiting for React Native context... retry $retryCount/$maxRetries")
                handler.postDelayed({ checkForModule() }, retryDelayMs)
            } else {
                android.util.Log.e("AuthenticationSession", "React Native context not ready after $maxRetries retries")
                callback(null)
            }
        }

        checkForModule()
    }
}
