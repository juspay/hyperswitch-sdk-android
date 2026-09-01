package io.hyperswitch.react

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import io.hyperswitch.paymentsession.SavedMethodConfirmationRegistry
import io.hyperswitch.paymentsession.SavedMethodsRequestRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import io.hyperswitch.paymentsession.PaymentSessionRouter

class HyperHeadlessModule internal constructor(
    rct: ReactApplicationContext,
    private val sessionRouter: PaymentSessionRouter,
) : io.hyperswitch.react.codegen.NativeHyperHeadlessSpec(rct) {

    override fun getPaymentSession(
        sdkAuthorization: String,
        paymentIntentData: ReadableMap,
        defaultPaymentMethod: ReadableMap,
        savedPaymentMethods: ReadableArray,
        callback: Callback
    ) {
        val request = SavedMethodsRequestRegistry.take(sdkAuthorization) ?: return
        if (!request.isCurrent(sdkAuthorization)) {
            request.callback(PaymentSessionHandlerImpl.stale())
            return
        }
        val handler = PaymentSessionHandlerImpl(
            sdkAuthorization = sdkAuthorization,
            currentSdkAuthorization = request.currentSdkAuthorization,
            defaultMethodData = paymentIntentData,
            lastUsedMethodData = defaultPaymentMethod,
            allMethodsData = savedPaymentMethods,
            jsCallback = callback,
            sessionRouter = sessionRouter,
            onTerminalResult = request.onTerminalResult,
        )
        request.callback(handler)
    }

    // rootTag is part of the wire contract for iOS's CVC-widget lookup; Android's
    // confirmation registry is keyed by sdkAuthorization and ignores it.
    override fun exitHeadless(sdkAuthorization: String, rootTag: Double, result: ReadableMap) {
        SavedMethodConfirmationRegistry.complete(sdkAuthorization, result)
    }

    /**
     * Completion signal for one payment's prefetch. The payload itself lives only in the JS
     * PrefetchCache (shared VM); native just resumes the awaiting launcher. The argument map
     * is `{sdkAuthorization}`-only and is merely echoed back to the (discarded) await result.
     */
    override fun completePrefetch(data: ReadableMap) {
        val sdkAuthorization = data.getString("sdkAuthorization") ?: return
        inFlightPrefetches.remove(sdkAuthorization)?.complete(data)
    }

    companion object {
        internal val inFlightPrefetches =
            ConcurrentHashMap<String, CompletableDeferred<ReadableMap>>()
    }
}
