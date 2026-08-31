package io.hyperswitch.react

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import io.hyperswitch.paymentsession.SavedMethodConfirmationRegistry
import io.hyperswitch.paymentsession.SavedMethodsRequestRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

@ReactModule(name = "HyperHeadless")
class HyperHeadlessModule internal constructor(rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    override fun getName(): String = "HyperHeadless"

    @ReactMethod
    fun getPaymentSession(
        sdkAuthorization: String,
        getPaymentMethodData: ReadableMap,
        getPaymentMethodData2: ReadableMap,
        getPaymentMethodDataArray: ReadableArray,
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
            defaultMethodData = getPaymentMethodData,
            lastUsedMethodData = getPaymentMethodData2,
            allMethodsData = getPaymentMethodDataArray,
            jsCallback = callback,
            onTerminalResult = request.onTerminalResult,
        )
        request.callback(handler)
    }

    @ReactMethod
    fun exitHeadless(sdkAuthorization: String, status: String) {
        SavedMethodConfirmationRegistry.complete(sdkAuthorization, status)
    }

    /**
     * Completion signal for one payment's prefetch. The payload itself lives only in the JS
     * PrefetchCache (shared VM); native just resumes the awaiting launcher. The argument map
     * is `{sdkAuthorization}`-only and is merely echoed back to the (discarded) await result.
     */
    @ReactMethod
    fun completePrefetch(data: ReadableMap) {
        val sdkAuthorization = data.getString("sdkAuthorization") ?: return
        inFlightPrefetches.remove(sdkAuthorization)?.complete(data)
    }

    companion object {
        internal val inFlightPrefetches =
            ConcurrentHashMap<String, CompletableDeferred<ReadableMap>>()
    }
}
