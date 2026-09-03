package io.hyperswitch.react

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import io.hyperswitch.paymentsession.HeadlessConfirmationRegistry
import io.hyperswitch.paymentsession.HeadlessRequestRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

class HyperHeadlessModule internal constructor(
    rct: ReactApplicationContext,
) : io.hyperswitch.react.codegen.NativeHyperHeadlessSpec(rct) {

    override fun getPaymentSession(
        sdkAuthorization: String,
        paymentIntentData: ReadableMap,
        defaultPaymentMethod: ReadableMap,
        savedPaymentMethods: ReadableArray,
        callback: Callback
    ) {
        val request = HeadlessRequestRegistry.take(sdkAuthorization) ?: return
        val handler = PaymentSessionHandlerImpl(
            sdkAuthorization = sdkAuthorization,
            currentSdkAuthorization = request.currentSdkAuthorization,
            defaultMethodData = paymentIntentData,
            lastUsedMethodData = defaultPaymentMethod,
            allMethodsData = savedPaymentMethods,
            jsCallback = callback,
            onTerminalResult = request.onTerminalResult,
        )
        request.callback(handler)
    }

    // Keyed by sdkAuthorization; the confirmation registry is the single completion channel.
    override fun exitHeadless(sdkAuthorization: String, result: ReadableMap) {
        HeadlessConfirmationRegistry.complete(sdkAuthorization, result)
    }

    // Completion signal for one payment's prefetch; the payload lives only in the JS PrefetchCache.
    override fun completePrefetch(data: ReadableMap) {
        val sdkAuthorization = data.getString("sdkAuthorization") ?: return
        inFlightPrefetches.remove(sdkAuthorization)?.complete(data)
    }

    companion object {
        internal val inFlightPrefetches =
            ConcurrentHashMap<String, CompletableDeferred<ReadableMap>>()
    }
}
