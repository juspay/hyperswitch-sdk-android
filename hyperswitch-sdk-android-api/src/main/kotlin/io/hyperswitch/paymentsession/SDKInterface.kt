package io.hyperswitch.paymentsession

import io.hyperswitch.PaymentEventListener
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet

/** Rendering-layer contract shared by both WebView and React Native backends. */
interface PresentationInterface {
    fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?
    ): Boolean

    fun presentSheet(configurationMap: Map<String, Any?>): Boolean

    /**
     * Presents the sheet with the merchant's completion and event listener attached to the
     * presentation itself. Backends that cannot own a presentation fall back to the one-shot
     * [PaymentSheetCallbackManager]. Returns true when presented as a fragment.
     */
    fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?,
        subscribedEvents: List<String>,
        eventListener: PaymentEventListener?,
        onResult: ((PaymentResult) -> Unit)?,
    ): Boolean {
        val isFragment = presentSheet(sessionConfig, configuration)
        onResult?.let { PaymentSheetCallbackManager.setCallback(it, isFragment) }
        return isFragment
    }

    fun presentSheet(
        configurationMap: Map<String, Any?>,
        subscribedEvents: List<String>,
        eventListener: PaymentEventListener?,
        onResult: ((PaymentResult) -> Unit)?,
    ): Boolean {
        val isFragment = presentSheet(configurationMap)
        onResult?.let { PaymentSheetCallbackManager.setCallback(it, isFragment) }
        return isFragment
    }
}

/** React Native lifecycle operations — only meaningful in the full SDK. */
interface ReactNativeLifecycle {
    var sessionConfig: PaymentSessionConfiguration?

    fun initializeReactNativeInstance()

    /** The session's identity in JS (its prefetch surface's root tag); null until prefetched. */
    val sessionTag: Int?
        get() = null

    /**
     * Starts a saved-payment-methods surface and hands its handler to [onHandler] once.
     * Default no-op for backends without headless support.
     */
    fun startSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
        onHandler: (PaymentSessionHandler) -> Unit,
    ) {}

    /**
     * Warms the intent-scoped API calls on a viewless surface under the current
     * [sessionConfig]; called again, it moves that surface to the new credentials.
     * No-op on the WebView backend.
     */
    fun prefetch() {}

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

    /** Stops every surface this session started. Default no-op. */
    fun close() {}
}

/** Combined interface implemented by the full SDK's React Native backend. */
interface SDKInterface : PresentationInterface, ReactNativeLifecycle
