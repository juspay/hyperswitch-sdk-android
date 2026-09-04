package io.hyperswitch.react

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import io.hyperswitch.paymentsession.PaymentSessionRouter

class HyperHeadlessModule internal constructor(
    rct: ReactApplicationContext,
    private val sessionRouter: PaymentSessionRouter,
) : io.hyperswitch.react.codegen.NativeHyperHeadlessSpec(rct) {

    override fun getPaymentSession(
        rootTag: Double,
        paymentIntentData: ReadableMap,
        defaultPaymentMethod: ReadableMap,
        savedPaymentMethods: ReadableArray,
        callback: Callback
    ) {
        val handler = PaymentSessionHandlerImpl(
            sdkAuthorization = sessionRouter.getSdkAuthorization(),
            defaultMethodData = paymentIntentData,
            lastUsedMethodData = defaultPaymentMethod,
            allMethodsData = savedPaymentMethods,
            jsCallback = callback,
            sessionRouter = sessionRouter,
        )
        sessionRouter.executeSessionCallback(handler)
    }

    override fun exitHeadless(rootTag: Double, result: ReadableMap) {
        sessionRouter.executeExitCallback(rootTag.toInt(), result.toExitResultJson())
    }

    // Completion signal for one payment's prefetch; the payload lives only in the JS PrefetchCache.
    override fun completePrefetch(rootTag: Double, data: ReadableMap) {
        sessionRouter.completePrefetchCallback(data)
    }
}
