package io.hyperswitch.react

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import io.hyperswitch.paymentsession.ExitHeadlessCallBackManager
import io.hyperswitch.paymentsession.GetPaymentSessionCallBackManager
import io.hyperswitch.paymentsession.PaymentSessionHandlerImpl

@ReactModule(HyperHeadlessModule.NAME)
class HyperHeadlessModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = NAME

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

    companion object {
        const val NAME = "HyperHeadless"
    }
}
