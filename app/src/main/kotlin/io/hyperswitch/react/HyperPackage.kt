package io.hyperswitch.react

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import io.hyperswitch.react.tapcard.TapCardModule

class HyperPackage : ReactPackage {
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(GooglePayButtonManager())
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        val modules = mutableListOf<NativeModule>(
            HyperModule(reactContext),
            HyperHeadlessModule(reactContext)
        )

        // Conditionally add TapCardModule only if tapcard SDK and NFC are available
        if (TapCardModule.isSdkAvailable(reactContext)) {
            modules.add(TapCardModule(reactContext))
        }

        return modules
    }
}