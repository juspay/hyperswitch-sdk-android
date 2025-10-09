package io.hyperswitch.authentication

/**
 * Type aliases and wrappers for 3DS authentication types
 * 
 * These allow merchants to use io.hyperswitch.authentication.* namespace
 * while the actual implementation comes from io.hyperswitch.modular_3ds
 */

// Session and Transaction types
typealias Session = io.hyperswitch.modular_3ds.api.ThreeDSSession
typealias Transaction = io.hyperswitch.modular_3ds.api.ThreeDSTransaction

// Configuration and Parameters
typealias AuthenticationConfiguration = io.hyperswitch.modular_3ds.api.AuthenticationConfiguration
typealias AuthenticationRequestParameters = io.hyperswitch.modular_3ds.api.AuthenticationRequestParameters
typealias ChallengeParameters = io.hyperswitch.modular_3ds.models.ChallengeParameters

// Callbacks
typealias ChallengeStatusReceiver = io.hyperswitch.modular_3ds.api.ChallengeStatusReceiver

// Events
typealias CompletionEvent = io.hyperswitch.modular_3ds.api.CompletionEvent
typealias ProtocolErrorEvent = io.hyperswitch.modular_3ds.api.ProtocolErrorEvent
typealias RuntimeErrorEvent = io.hyperswitch.modular_3ds.api.RuntimeErrorEvent
typealias ErrorMessage = io.hyperswitch.modular_3ds.api.ErrorMessage

// Environment and Customization
typealias ThreeDSEnvironment = io.hyperswitch.modular_3ds.models.ThreeDSEnvironment
typealias UiCustomization = io.hyperswitch.modular_3ds.models.UiCustomization
typealias ToolbarCustomization = io.hyperswitch.modular_3ds.models.ToolbarCustomization

// Provider Registration
typealias ProviderRegistry = io.hyperswitch.modular_3ds.provider.ProviderRegistry
typealias ProviderFactory = io.hyperswitch.modular_3ds.provider.ProviderFactory

/**
 * Wrapper for AuthenticationResult sealed class
 * This is needed because type aliases don't work well with sealed classes and their nested types
 */
sealed class AuthenticationResult {
    object Success : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
    object Challenge : AuthenticationResult()
    
    companion object {
        /**
         * Convert from modular_3ds AuthenticationResult to wrapper AuthenticationResult
         */
        internal fun from(result: io.hyperswitch.modular_3ds.api.AuthenticationResult): AuthenticationResult {
            return when (result) {
                is io.hyperswitch.modular_3ds.api.AuthenticationResult.Success -> Success
                is io.hyperswitch.modular_3ds.api.AuthenticationResult.Error -> Error(result.message)
                is io.hyperswitch.modular_3ds.api.AuthenticationResult.Challenge -> Challenge
            }
        }
    }
}
