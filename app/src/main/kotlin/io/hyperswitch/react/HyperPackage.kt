package io.hyperswitch.react

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import java.util.ArrayList

class HyperPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return when (name) {
            HyperswitchSdkNativeModule.NAME -> HyperswitchSdkNativeModule(reactContext)
            else -> null
        }
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        val viewManagers: MutableList<ViewManager<*, *>> = ArrayList()
        viewManagers.add(GooglePayButtonManager())
        return viewManagers
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            arrayOf(HyperswitchSdkNativeModule.NAME).associateWith {
                ReactModuleInfo(it, it, false, false, false, true)
            }.toMutableMap()
        }
    }
}