package io.hyperswitch.authentication

import android.app.Activity
import io.hyperswitch.modular_3ds.api.ThreeDSAuthenticationSession


class AuthenticationSession(
    private val activity: Activity,
    private val publishKey: String
) {
    companion object {
        /**
         * Check if the 3DS library is available at runtime
         * 
         * @return true if modular_3ds library is available, false otherwise
         */
        @JvmStatic
        fun isAvailable(): Boolean {
            return try {
                Class.forName("io.hyperswitch.modular_3ds.api.ThreeDSAuthenticationSession")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }
    
    private val internalAuthSession: ThreeDSAuthenticationSession
    
    init {
        if (!isAvailable()) {
            throw IllegalStateException(
                "3DS library not found. Please add the following dependency to your build.gradle:\n" +
                "implementation 'io.hyperswitch:modular-3ds-api:x.x.x'\n" +
                "You can check availability using AuthenticationSession.isAvailable() before instantiation."
            )
        }
        internalAuthSession = ThreeDSAuthenticationSession(activity, publishKey)
    }

    /**
     * Initialize a 3DS session
     * 
     * @param authIntentClientSecret The authentication intent client secret
     * @param configuration Authentication configuration (use AuthenticationConfiguration from this package)
     * @param callback Callback to receive authentication result (use AuthenticationResult from this package)
     * @return Session object if initialization is successful, null otherwise
     */
    fun initThreeDsSession(
        authIntentClientSecret: String,
        configuration: AuthenticationConfiguration,
        callback: (AuthenticationResult) -> Unit
    ): Session? {
        return internalAuthSession.initThreeDsSession(
            authIntentClientSecret = authIntentClientSecret,
            configuration = configuration
        ) { modularResult ->
            // Convert modular_3ds result to wrapper result
            callback(AuthenticationResult.from(modularResult))
        }
    }
}
