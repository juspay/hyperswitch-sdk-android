package io.hyperswitch.react

import android.os.Bundle
import com.facebook.react.ReactFragment
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
}

class HyperFragmentBuilder {
    private var componentName: String? = null
    private var launchOptions: Bundle? = null
    private var fabricEnabled: Boolean = false

    fun setComponentName(componentName: String): HyperFragmentBuilder {
        this.componentName = componentName
        return this
    }

    fun setLaunchOptions(launchOptions: Bundle): HyperFragmentBuilder {
        this.launchOptions = launchOptions
        return this
    }

    fun setFabricEnabled(fabricEnabled: Boolean): HyperFragmentBuilder {
        this.fabricEnabled = fabricEnabled
        return this
    }

    fun build(): HyperFragment {
        val fragment = HyperFragment()
        val args = Bundle()
        componentName?.let { args.putString("componentName", it) }
        launchOptions?.let { args.putBundle("launchOptions", it) }
        args.putBoolean("fabricEnabled", fabricEnabled)
        fragment.arguments = args
        return fragment
    }
}
