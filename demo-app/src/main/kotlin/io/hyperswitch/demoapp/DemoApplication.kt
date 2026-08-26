package io.hyperswitch.demoapp

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import io.hyperswitch.react.ReactNativeController

class DemoApplication : Application(), ReactApplication {

    override fun onCreate() {
        super.onCreate()
        ReactNativeController.initialize(this)
    }

    override val reactHost: ReactHost
        get() = ReactNativeController.getReactHost()
}
