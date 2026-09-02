package io.hyperswitch.vault.react

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class HyperVaultPackage : TurboReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        when (name) {
            HyperVaultModule.NAME -> HyperVaultModule(reactContext)
            else -> null
        }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            mapOf(
                HyperVaultModule.NAME to ReactModuleInfo(
                    HyperVaultModule.NAME,
                    HyperVaultModule::class.java.name,
                    false, // canOverrideExistingModule
                    false, // needsEagerInit
                    false, // isCxxModule
                    true,  // isTurboModule
                )
            )
        }
}
