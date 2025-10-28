package io.hyperswitch.paymentsession

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactApplication
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlags
import com.facebook.react.ReactFragment
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
import io.hyperswitch.BuildConfig
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.paymentIntentClientSecret
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity

class ReactNativeUtils(private val activity: Activity) : SDKInterface {

    private var reactHost: ReactHost? = null
    private var reactNativeHost: ReactNativeHost? = null
    private var reactContext: ReactContext? = null
    private var headlessTaskId: Int? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME)

    @SuppressLint("VisibleForTests")
    override fun initializeReactNativeInstance() {
        reactContext = try {
            val application = (activity.application as ReactApplication)
            reactHost = application.reactHost
            reactNativeHost = application.reactNativeHost
            if (ReactNativeFeatureFlags.enableBridgelessArchitecture()) {
                val reactHost = checkNotNull(reactHost) { "ReactHost is not initialized in New Architecture" }
                reactHost.currentReactContext
            } else {
                val reactInstanceManager = reactNativeHost?.reactInstanceManager
                reactInstanceManager?.currentReactContext
            }
        } catch (ex: ClassCastException) {
            throw IllegalStateException(
                "Application is not a ReactApplication. " + "Please ensure you've set up React Native correctly.",
                ex
            )
        } catch (ex: RuntimeException) {
            throw IllegalStateException(
                "Failed to initialize React Native instance. " + "Please check your AndroidManifest.xml and React Native configuration.",
                ex
            )
        }
    }

    override fun recreateReactContext() {
        activity.runOnUiThread {
            val context = reactContext
            if (context == null) {
                if (ReactNativeFeatureFlags.enableBridgelessArchitecture()) {
                    val reactHost = checkNotNull(reactHost)
                    reactHost.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                invokeStartTask(context)
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
                                invokeStartTask(context)
                                reactInstanceManager.removeReactInstanceEventListener(this)
                            }
                        }
                    )
                    reactInstanceManager?.createReactContextInBackground()
                }
            } else {
                invokeStartTask(context)
            }
        }
    }

    private fun invokeStartTask(reactContext: ReactContext) {
        val taskConfig = HeadlessJsTaskConfig(
            "HyperHeadless", Arguments.fromBundle(
                launchOptions.getBundle(
                    reactContext,
                    paymentIntentClientSecret ?: ""
                )
            ), 5000, true, null
        )

        val headlessJsTaskContext = HeadlessJsTaskContext.Companion.getInstance(reactContext)
        UiThreadUtil.runOnUiThread {
            headlessTaskId?.let {
                headlessJsTaskContext.finishTask(it)
            }
            headlessTaskId = headlessJsTaskContext.startTask(taskConfig)
        }
    }

    override fun presentSheet(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ): Boolean {
        val bundle = launchOptions.getBundle(paymentIntentClientSecret, configuration)
        applyFonts(configuration, bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        return presentSheet(
            bottomInsetToDIPFromPixel(
                launchOptions.getBundleWithHyperParams(
                    configurationMap
                )
            )
        )
    }

    private fun presentSheet(bundle: Bundle): Boolean {
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            val newReactNativeFragmentSheet =
                ReactFragment.Builder().setComponentName("hyperSwitch").setLaunchOptions(bundle)
                    .setFabricEnabled(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED).build()

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
                ?.getBundle("typography")?.let { typography ->
                    typography.remove("fontResId")
                    typography.putString(
                        "fontResId",
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
            bundle.getBundle("configuration")?.getBundle("appearance")?.getBundle("primaryButton")
                ?.getBundle("typography")?.let { typography ->
                    typography.remove("fontResId")
                    typography.putString(
                        "fontResId",
                        activity.resources.getResourceName(it).toString().split("/")[1]
                    )
                }
        }
    }

    private fun bottomInsetToDIPFromPixel(bundle: Bundle): Bundle {
        val propsBundle = bundle.getBundle("props")
        val hyperParamsBundle = propsBundle?.getBundle("hyperParams")
        hyperParamsBundle?.getFloat("topInset")?.let { dipValue ->
            hyperParamsBundle.putFloat("topInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        hyperParamsBundle?.getFloat("leftInset")?.let { dipValue ->
            hyperParamsBundle.putFloat("leftInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        hyperParamsBundle?.getFloat("rightInset")?.let { dipValue ->
            hyperParamsBundle.putFloat("rightInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        hyperParamsBundle?.getFloat("bottomInset")?.let { dipValue ->
            hyperParamsBundle.putFloat("bottomInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        return bundle
    }
}