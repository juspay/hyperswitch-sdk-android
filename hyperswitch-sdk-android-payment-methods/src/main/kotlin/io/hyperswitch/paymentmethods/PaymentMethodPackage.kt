package io.hyperswitch.paymentmethods

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

/** Registers [PaymentMethodModule] on a payment-method session's dedicated React host. */
internal class PaymentMethodPackage(
    private val emitter: PaymentMethodEventEmitter,
) : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == PaymentMethodModule.NAME) PaymentMethodModule(reactContext, emitter) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            mapOf(
                PaymentMethodModule.NAME to ReactModuleInfo(
                    PaymentMethodModule.NAME,
                    PaymentMethodModule.NAME,
                    canOverrideExistingModule = false,
                    needsEagerInit = false,
                    isCxxModule = false,
                    isTurboModule = true,
                ),
            )
        }
}
