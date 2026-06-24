package io.hyperswitch.paymentsession

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.ReactNativeHost
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.core.BuildConfig as CoreBuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.react.HyperswitchBuildConfig
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.sessionConfig
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperEventEmitter
import io.hyperswitch.react.HyperFragment

class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface {

    private var reactHost: ReactHost? = null
    private var reactNativeHost: ReactNativeHost? = null
    private var reactContext: ReactContext? = null
    private var headlessTaskId: Int? = null
    private val launchOptions = LaunchOptions(activity, CoreBuildConfig.VERSION_NAME, hsConfig)

    @SuppressLint("VisibleForTests")
    override fun initializeReactNativeInstance() {
        reactContext = try {
            // Get ReactNativeHost from ReactNativeController singleton instead of casting Application to ReactApplication
            // This allows merchants to use their own Application class without extending MainApplication
            if (!ReactNativeController.getIsInitialized()){
                ReactNativeController.initialize(activity.application)
            }
            reactNativeHost = ReactNativeController.getReactNativeHost()
            reactHost = ReactNativeController.getReactHost()

            if (HyperswitchBuildConfig.isNewArchitectureEnabled) {
                val reactHost = checkNotNull(reactHost) { "ReactHost is not initialized in New Architecture" }
                reactHost.currentReactContext
            } else {
                val reactInstanceManager = reactNativeHost?.reactInstanceManager
                reactInstanceManager?.currentReactContext
            }
        } catch (ex: IllegalStateException) {
            throw IllegalStateException(
                "HyperSDK not initialized. Please call HyperSDK.initialize() in your Application.onCreate()",
                ex
            )
        } catch (ex: RuntimeException) {
            throw IllegalStateException(
                "Failed to initialize React Native instance. " + "Please check your AndroidManifest.xml and React Native configuration.",
                ex
            )
        }
    }

    override fun recreateReactContext(configuration: SavedPaymentMethodsConfiguration?) {
        activity.runOnUiThread {
            val context = reactContext
            if (context == null) {
            if (HyperswitchBuildConfig.isNewArchitectureEnabled) {
                val reactHost = checkNotNull(reactHost)
                reactHost.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                invokeStartTask(context, configuration)
                                reactHost.removeReactInstanceEventListener(this)
                            }
                        }
                    )
                    reactHost.start()
                } else {
                    val reactInstanceManager = reactNativeHost?.reactInstanceManager
                    reactInstanceManager?.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                invokeStartTask(context, configuration)
                                reactInstanceManager.removeReactInstanceEventListener(this)
                            }
                        }
                    )
                    reactInstanceManager?.createReactContextInBackground()
                }
            } else {
                invokeStartTask(context, configuration)
            }
        }
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { HyperEventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

    private fun invokeStartTask(reactContext: ReactContext, configuration: SavedPaymentMethodsConfiguration? = null) {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundle(
            reactContext,
            sessionConfig,
            null,
            subscribedEvents,
        )
        configuration?.let { config ->
            bundle.getBundle("props")?.putBundle("configuration", config.bundle)
        }
        val taskConfig = HeadlessJsTaskConfig(
            "HyperHeadless", Arguments.fromBundle(bundle), 5000, true, null
        )

        val headlessJsTaskContext = HeadlessJsTaskContext.getInstance(reactContext)
        UiThreadUtil.runOnUiThread {
            headlessTaskId?.let {
                headlessJsTaskContext.finishTask(it)
            }
            headlessTaskId = headlessJsTaskContext.startTask(taskConfig)
        }
    }

    override fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?
    ): Boolean {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundle(sessionConfig, configuration, subscribedEvents)
        applyFonts(configuration, bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        val subscribedEvents = getSubscribedEventsSafely()
        return presentSheet(
            bottomInsetToDIPFromPixel(
                launchOptions.getBundleWithHyperParams(
                    configurationMap,
                    subscribedEvents,
                    sessionConfig
                )
            )
        )
    }

    private fun presentSheet(bundle: Bundle): Boolean {
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            val newReactNativeFragmentSheet =
                HyperFragment.Builder().setComponentName("hyperSwitch").setLaunchOptions(bundle)
                    .setFabricEnabled(HyperswitchBuildConfig.isNewArchitectureEnabled).build()

            val activity2 = activity as FragmentActivity

            activity2.onBackPressedDispatcher.addCallback {
                newReactNativeFragmentSheet.onBackPressed()
                // activity2.onBackPressedDispatcher.onBackPressed()
            }

            activity2.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, newReactNativeFragmentSheet, "paymentSheet")
                .commitAllowingStateLoss()

            return true
        } else {
            activity.startActivity(
                Intent(
                    activity.applicationContext,
                    HyperActivity::class.java
                ).apply {
                    putExtra("flow", 1)
                    putExtra("configuration", bundle)
                })

            return false
        }
    }

    private fun applyFonts(configuration: PaymentSheet.Configuration?, bundle: Bundle) {
        configuration?.appearance?.typography?.fontResId?.let {
            ReactFontManager.getInstance().addCustomFont(
                activity,
                activity.resources.getResourceName(it).toString().split("/")[1],
                it
            )
            bundle.getBundle("props")?.getBundle("configuration")?.getBundle("appearance")
                ?.getBundle("font")?.let { font ->
                    font.remove("fontResId")
                    font.putString(
                        "family",
                        activity.resources.getResourceName(it).toString().split("/")[1]
                    )
                }
        }

        configuration?.appearance?.primaryButton?.typography?.fontResId?.let {
            ReactFontManager.getInstance().addCustomFont(
                activity,
                activity.resources.getResourceName(it).toString().split("/")[1],
                it
            )
            bundle.getBundle("props")?.getBundle("configuration")?.getBundle("appearance")
                ?.getBundle("primaryButton")?.getBundle("typography")?.let { typography ->
                    typography.remove("fontResId")
                    typography.putString(
                        "family",
                        activity.resources.getResourceName(it).toString().split("/")[1]
                    )
                }
        }
    }

    private fun bottomInsetToDIPFromPixel(bundle: Bundle): Bundle {
        val propsBundle = bundle.getBundle("props")
        val sdkParamsBundle = propsBundle?.getBundle("sdkParams")
        sdkParamsBundle?.getFloat("topInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("topInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("leftInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("leftInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("rightInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("rightInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("bottomInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("bottomInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        return bundle
    }
}