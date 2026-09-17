package io.hyperswitch.react

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.proyecto26.inappbrowser.ChromeTabsDismissedEvent
import com.proyecto26.inappbrowser.ChromeTabsManagerActivity
import io.hyperswitch.redirect.RedirectEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/** Fallback presentation for hosts that are not a FragmentActivity. */
class HyperActivity : ReactActivity() {

    override fun getMainComponentName(): String = "hyperSwitch"

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        return object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
            override fun getLaunchOptions(): Bundle =
                intent.getBundleExtra("configuration") ?: Bundle()

            override fun getReactHost(): ReactHost = ReactNativeController.runtime.reactHost
        }
    }

    @Subscribe
    fun onEvent(event: RedirectEvent) {
        unRegisterEventBus()
        EventBus.getDefault().post(
            ChromeTabsDismissedEvent(
                event.message,
                event.resultType,
                event.isError
            )
        )
        startActivity(ChromeTabsManagerActivity.createDismissIntent(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The OS can recreate this activity after process death before the host app
        // initialised the SDK; initialize() is idempotent.
        ReactNativeController.initialize(application)
        super.onCreate(savedInstanceState)
        registerEventBus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unRegisterEventBus()
    }

    private fun registerEventBus() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    private fun unRegisterEventBus() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
