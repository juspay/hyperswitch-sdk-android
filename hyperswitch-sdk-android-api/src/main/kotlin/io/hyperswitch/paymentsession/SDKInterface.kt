package io.hyperswitch.paymentsession

import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet

/** Rendering-layer contract shared by both WebView and React Native backends. */
interface PresentationInterface {
    fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?
    ): Boolean

    fun presentSheet(configurationMap: Map<String, Any?>): Boolean
}

/** React Native lifecycle operations — only meaningful in the full SDK. */
interface ReactNativeLifecycle {
    var sessionConfig: PaymentSessionConfiguration?

    fun initializeReactNativeInstance()
    fun recreateReactContext(configuration: SavedPaymentMethodsConfiguration? = null)

    /** Warms the intent-scoped API calls on a viewless surface. No-op on the WebView backend. */
    fun prefetch() {}

    /** Tears down whatever [prefetch] started. Default no-op. */
    fun disposePrefetch() {}

    /** Resolves once the session's runtime is ready to present. Default: immediately. */
    suspend fun awaitReady() {}

    /** Replaces the session's intent. Default: swap the authorization with no refresh. */
    fun updateIntent(
        authorizationProvider: (onAuthorization: (String) -> Unit) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        authorizationProvider { auth ->
            sessionConfig = PaymentSessionConfiguration(auth)
            onResult(Result.success(auth))
        }
    }
}

/** Combined interface implemented by the full SDK's React Native backend. */
interface SDKInterface : PresentationInterface, ReactNativeLifecycle