package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.hyperswitch.paymentsession.ExitHeadlessCallBackManager
import io.hyperswitch.paymentsession.GetPaymentSessionCallBackManager
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl
import java.util.concurrent.ConcurrentHashMap

class HyperHeadlessModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {

    // Transient routing table: sdkAuthorization → callback that stores data on the launcher instance.
    // Entries are added before a prefetch task starts and removed immediately after firing.
    val pendingCallbacks = ConcurrentHashMap<String, (ReadableMap) -> Unit>()

    override fun getName(): String = "HyperHeadless"

    @ReactMethod
    fun getPaymentSession(
        rootTag: Int,
        getPaymentMethodData: ReadableMap,
        getPaymentMethodData2: ReadableMap,
        getPaymentMethodDataArray: ReadableArray,
        callback: Callback
    ) {
        val handler = PaymentSessionHandlerImpl(
            sdkAuthorization = GetPaymentSessionCallBackManager.getSdkAuthorization(),
            defaultMethodData = getPaymentMethodData,
            lastUsedMethodData = getPaymentMethodData2,
            allMethodsData = getPaymentMethodDataArray,
            jsCallback = callback,
        )
        GetPaymentSessionCallBackManager.executeCallback(handler)
    }

    @ReactMethod
    fun exitHeadless(rootTag: Int, status: String) {
        ExitHeadlessCallBackManager.executeCallback(rootTag, status)
    }

    @ReactMethod
    fun storePrefetchedApiData(rootTag: Int, data: ReadableMap) {
        val sdkAuth = data.getString("sdkAuthorization")
        if (sdkAuth != null) {
            pendingCallbacks.remove(sdkAuth)?.invoke(data)
        }
        val writableData = Arguments.createMap()
        writableData.merge(data)
        rct.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("prefetchApiDataReady", writableData)
    }
}
