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
    fun initializeReactNativeInstance()
    fun recreateReactContext()
}

/** Combined interface implemented by the full SDK's React Native backend. */
interface SDKInterface : PresentationInterface, ReactNativeLifecycle