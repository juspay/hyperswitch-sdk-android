package io.hyperswitch.react

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class HyperPackage : ReactPackage {
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(GooglePayButtonManager())
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(HyperModule(reactContext), HyperHeadlessModule(reactContext))
    }
}