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
     * Initialize Authentication Session
     * @param paymentIntentClientSecret The client secret for the payment intent
     * @param authenticationConfiguration Configuration object containing 3DS SDK settings
     * @param callback Callback to handle initialization result
     * @return Session object for further operations
     */
    fun initAuthenticationSession(
        paymentIntentClientSecret: String,
        authenticationConfiguration: AuthenticationConfiguration,
        callback: (AuthenticationResult) -> Unit
    ): Session {
        try {
            // Store the client secret for the session
            AuthenticationSession.paymentIntentClientSecret = paymentIntentClientSecret
            isPresented = false // This is for authentication, not presentation
            
            // Create the bundle for React Native context
            val bundle = launchOptions.getAuthenticationBundle(paymentIntentClientSecret)
            
            // Recreate React context to ensure fresh state
            reactNativeUtils.recreateReactContext()
            
            // Wait for React Native context to be ready
            waitForReactNativeContext { hyperHeadlessModule ->
                if (hyperHeadlessModule != null) {
                    // Update the session with the ready module
                    currentSession?.let { session ->
                        // Use reflection to update the hyperHeadlessModule field
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
            
            // Create and store session (will be updated when React Native is ready)
            currentSession = Session(
                activity = activity,
                publishableKey = publishableKey,
                paymentIntentClientSecret = paymentIntentClientSecret,
                uiCustomization = authenticationConfiguration.uiCustomization,
                hyperHeadlessModule = null // Will be set when React Native is ready
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
        val maxRetries = 20 // Increased retries
        val retryDelayMs = 250L // Shorter delay

        fun checkForModule() {
            val hyperHeadlessModule = io.hyperswitch.react.HyperHeadlessModule.getInstance()
            if (hyperHeadlessModule != null) {
                android.util.Log.d("AuthenticationSession", "React Native context ready after $retryCount retries")
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
