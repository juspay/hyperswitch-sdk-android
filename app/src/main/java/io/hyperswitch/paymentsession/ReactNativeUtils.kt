package io.hyperswitch.paymentsession

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactApplication
import com.facebook.react.ReactFragment
import com.facebook.react.ReactInstanceManager
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import io.hyperswitch.BuildConfig
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity

class ReactNativeUtils(private val activity: Activity) : SDKInterface {

    private var reactInstanceManager: ReactInstanceManager? = null
    private val launchOptions = LaunchOptions(activity)

    override fun initializeReactNativeInstance() {
        reactInstanceManager = try {
            (activity.application as ReactApplication).reactNativeHost.reactInstanceManager
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

    @SuppressLint("VisibleForTests")
    override fun recreateReactContext() {
        reactInstanceManager?.let {
            val reactContext = it.currentReactContext
            activity.runOnUiThread {
                if (reactContext == null || !reactContext.hasCatalystInstance()) {
                    it.createReactContextInBackground()
                } else {
                    it.recreateReactContextInBackground()
                }
            }
        } ?: throw IllegalStateException("Payment Session Initialization Failed")
    }

    override fun presentSheet(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ): Boolean {
        val bundle = launchOptions.getBundle(paymentIntentClientSecret, configuration)
        applyFonts(configuration, bundle)
        return presentSheet(bundle)
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        return presentSheet(launchOptions.toBundleWithHyperParams(configurationMap))
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
}