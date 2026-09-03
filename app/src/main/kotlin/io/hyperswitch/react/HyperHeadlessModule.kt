package io.hyperswitch.react

import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsession.ConfirmationCallback
import io.hyperswitch.paymentsession.HeadlessRegistry
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import io.hyperswitch.paymentsession.PendingHeadlessRequest
import io.hyperswitch.paymentsession.toPaymentResult
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
        val request = HeadlessRegistry.take<PendingHeadlessRequest>(
            HeadlessRegistry.Kind.REQUEST,
            sdkAuthorization,
        ) ?: run {
            Log.w(
                "HyperHeadlessModule",
                "getPaymentSession: no pending saved-methods request for this authorization; dropping late response"
            )
            return
        }
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

    // Keyed by sdkAuthorization; the confirm waiter is the single completion channel.
    override fun exitHeadless(sdkAuthorization: String, result: ReadableMap) {
        HeadlessRegistry.take<ConfirmationCallback>(HeadlessRegistry.Kind.CONFIRM, sdkAuthorization)
            ?.invoke(result.toPaymentResult())
    }

    // Completion signal for one payment's prefetch; the payload lives only in the JS PrefetchCache.
    override fun completePrefetch(data: ReadableMap) {
        val sdkAuthorization = data.getString("sdkAuthorization") ?: return
        HeadlessRegistry.take<CompletableDeferred<ReadableMap>>(HeadlessRegistry.Kind.PREFETCH, sdkAuthorization)
            ?.complete(data)
    }
}
