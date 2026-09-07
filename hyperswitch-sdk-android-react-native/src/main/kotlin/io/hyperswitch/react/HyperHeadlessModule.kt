package io.hyperswitch.react

import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsession.HeadlessAttempt

/** Stateless: every call carries the root tag of the surface it comes from. */
class HyperHeadlessModule internal constructor(
    private val rct: ReactApplicationContext,
) : io.hyperswitch.react.codegen.NativeHyperHeadlessSpec(rct) {

    override fun getPaymentSession(
        rootTag: Double,
        paymentIntentData: ReadableMap,
        defaultPaymentMethod: ReadableMap,
        savedPaymentMethods: ReadableArray,
        callback: Callback
    ) {
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            when (owner) {
                is HeadlessAttempt ->
                    owner.onPaymentSession(paymentIntentData, defaultPaymentMethod, savedPaymentMethods, callback)
                else -> Log.w(TAG, "getPaymentSession: no headless owner for rootTag=$rootTag")
            }
        }
    }

    override fun exitHeadless(rootTag: Double, result: ReadableMap) {
        val json = result.toExitResultJson()
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            when (owner) {
                is HeadlessAttempt -> owner.onExit(parsePaymentResult(json))
                is HyperFragment -> owner.notifyResult(CallbackType.CONFIRM_CVC_ACTION, json)
                else -> Log.w(TAG, "exitHeadless: no owner for rootTag=$rootTag")
            }
        }
    }

    private companion object {
        const val TAG = "HyperHeadlessModule"
    }

    override fun completePrefetch(
        rootTag: Double,
        data: ReadableMap?
    ) {
        TODO("Not yet implemented")
    }
}
