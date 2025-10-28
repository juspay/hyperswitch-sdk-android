package io.hyperswitch.paymentsession

import io.hyperswitch.paymentsheet.PaymentSheet

interface SDKInterface {
    fun presentSheet(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ): Boolean

    fun presentSheet(configurationMap: Map<String, Any?>): Boolean
    fun initializeReactNativeInstance()
    fun recreateReactContext()
}