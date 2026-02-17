package io.hyperswitch.react

import android.os.Bundle
import com.facebook.react.ReactFragment
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.proyecto26.inappbrowser.ChromeTabsDismissedEvent
import com.proyecto26.inappbrowser.ChromeTabsManagerActivity
import io.hyperswitch.redirect.RedirectEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class HyperFragment: ReactFragment() {

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
        startActivity(ChromeTabsManagerActivity.createDismissIntent(requireContext()))
    }

    override fun getReactNativeHost(): ReactNativeHost {
        return ReactNativeController.getReactNativeHost()
    }

    override fun getReactHost(): ReactHost {
        return ReactNativeController.getReactHost()
    }

    class Builder {
        var mComponentName: String? = null
        var mLaunchOptions: Bundle? = null
        var mFabricEnabled: Boolean? = false

        fun setComponentName(componentName: String?): Builder {
            this.mComponentName = componentName
            return this
        }

        fun setLaunchOptions(launchOptions: Bundle?): Builder {
            this.mLaunchOptions = launchOptions
            return this
        }

        fun build(): HyperFragment {
            val fragment = HyperFragment()
            val args = Bundle()
            args.putString("arg_component_name", mComponentName)
            args.putBundle("arg_launch_options", mLaunchOptions)
            args.putBoolean("arg_fabric_enabled", mFabricEnabled == true)
            fragment.setArguments(args)
            return fragment
        }

        fun setFabricEnabled(fabricEnabled: Boolean): Builder {
            this.mFabricEnabled = fabricEnabled
            return this
        }
    }
}

