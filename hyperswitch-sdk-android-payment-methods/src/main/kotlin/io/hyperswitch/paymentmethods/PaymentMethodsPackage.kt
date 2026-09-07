package io.hyperswitch.paymentmethods

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

/**
 * React package registering the payment-method session native modules
 * ([PaymentMethodsEventEmitterModule]) on this session's React host.
 */
class PaymentMethodsPackage : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        when (name) {
            PaymentMethodsEventEmitterModule.NAME -> PaymentMethodsEventEmitterModule(reactContext)
            else -> null
        }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            mapOf(
                PaymentMethodsEventEmitterModule.NAME to ReactModuleInfo(
                    PaymentMethodsEventEmitterModule.NAME,
                    PaymentMethodsEventEmitterModule.NAME,
                    canOverrideExistingModule = false,
                    needsEagerInit = false,
                    isCxxModule = false,
                    isTurboModule = true,
                ),
            )
        }
}
