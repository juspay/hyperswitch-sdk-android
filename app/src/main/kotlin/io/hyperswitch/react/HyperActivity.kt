package io.hyperswitch.react

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.proyecto26.inappbrowser.ChromeTabsDismissedEvent
import com.proyecto26.inappbrowser.ChromeTabsManagerActivity
import io.hyperswitch.redirect.RedirectEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class HyperActivity : ReactActivity() {

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override fun getMainComponentName(): String = "hyperSwitch"

    /**
     * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
     * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate {
        return object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
            override fun getLaunchOptions(): Bundle = intent.getBundleExtra("configuration")!!
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