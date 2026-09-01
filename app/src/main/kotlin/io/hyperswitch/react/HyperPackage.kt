package io.hyperswitch.react

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

class HyperPackage(
    private val eventEmitter: HyperEventEmitter,
) : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return when (name) {
            io.hyperswitch.react.codegen.NativeHyperModuleSpec.NAME -> HyperModule(reactContext, eventEmitter)
            io.hyperswitch.react.codegen.NativeHyperHeadlessSpec.NAME -> HyperHeadlessModule(reactContext)
            else -> null
        }
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(GooglePayButtonManager())
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            mapOf(
                io.hyperswitch.react.codegen.NativeHyperModuleSpec.NAME to ReactModuleInfo(
                    io.hyperswitch.react.codegen.NativeHyperModuleSpec.NAME,
                    io.hyperswitch.react.codegen.NativeHyperModuleSpec.NAME,
                    canOverrideExistingModule = false,
                    needsEagerInit = false,
                    isCxxModule = false,
                    isTurboModule = true,
                ),
                io.hyperswitch.react.codegen.NativeHyperHeadlessSpec.NAME to ReactModuleInfo(
                    io.hyperswitch.react.codegen.NativeHyperHeadlessSpec.NAME,
                    io.hyperswitch.react.codegen.NativeHyperHeadlessSpec.NAME,
                    canOverrideExistingModule = false,
                    needsEagerInit = false,
                    isCxxModule = false,
                    isTurboModule = true,
                ),
            )
        }
    }
}
