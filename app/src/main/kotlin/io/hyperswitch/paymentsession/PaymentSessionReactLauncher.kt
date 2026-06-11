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
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.paymentsession.DefaultPaymentSessionLauncher.Companion.sessionConfig
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperEventEmitter
import io.hyperswitch.react.HyperHeadlessModule

class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface {

    private var reactHost: ReactHost? = null
    private var reactNativeHost: ReactNativeHost? = null
    private var reactContext: ReactContext? = null
    private var headlessTaskId: Int? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME, hsConfig)

    // Per-instance prefetch state — no global store needed.
    @Volatile internal var isPrefetchTriggered: Boolean = false
    @Volatile internal var prefetchedData: ReadableMap? = null

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

            if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
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

    override fun recreateReactContext(
        configuration: SavedPaymentMethodsConfiguration?,
        headlessType: String
    ) {
        activity.runOnUiThread {
            val context = reactContext
            if (context == null) {
                if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
                    val reactHost = checkNotNull(reactHost)
                    reactHost.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                invokeStartTask(context, configuration, headlessType)
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
                                invokeStartTask(context, configuration, headlessType)
                                reactInstanceManager.removeReactInstanceEventListener(this)
                            }
                        }
                    )
                    reactInstanceManager?.createReactContextInBackground()
                }
            } else {
                invokeStartTask(context, configuration, headlessType)
            }
        }
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { HyperEventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

    private fun invokeStartTask(
        reactContext: ReactContext,
        configuration: SavedPaymentMethodsConfiguration? = null,
        headlessType: String = "savedPM"
    ) {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundle(
            reactContext,
            sessionConfig,
            null,
            subscribedEvents,
        )
        bundle.getBundle("props")?.putString("headlessType", headlessType)
        if (headlessType == "prefetch") {
            // Register callback on the module instance so data lands on this launcher.
            val sdkAuth = sessionConfig?.sdkAuthorization
            if (sdkAuth != null) {
                reactContext.getNativeModule(HyperHeadlessModule::class.java)
                    ?.pendingCallbacks
                    ?.set(sdkAuth) { data -> prefetchedData = data }
            }
        } else if (headlessType == "savedPM") {
            // Include already-prefetched data so HeadlessTask can skip API calls.
            val data = prefetchedData
            if (data != null) {
                bundle.getBundle("props")?.putBundle(
                    "prefetchedApiData",
                    launchOptions.toBundle(data.toHashMap())
                )
            } else if (isPrefetchTriggered) {
                bundle.getBundle("props")?.putBundle("prefetchedApiData", android.os.Bundle())
            }
        }
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
        addPrefetchedApiDataToBundle(bundle)
        applyFonts(configuration, bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundleWithHyperParams(configurationMap, subscribedEvents)
        addPrefetchedApiDataToBundle(bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    private fun addPrefetchedApiDataToBundle(bundle: Bundle) {
        val propsBundle = bundle.getBundle("props") ?: return
        val data = prefetchedData
        when {
            !isPrefetchTriggered -> {}
            data != null ->
                propsBundle.putBundle(
                    "prefetchedApiData",
                    launchOptions.toBundle(data.toHashMap())
                )
            else -> propsBundle.putBundle("prefetchedApiData", android.os.Bundle())
        }
    }

    private fun presentSheet(bundle: Bundle): Boolean {
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            val newReactNativeFragmentSheet =
                HyperFragment.Builder().setComponentName("hyperSwitch").setLaunchOptions(bundle)
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