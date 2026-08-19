package io.hyperswitch.paymentsession

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.ReactNativeHost
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperEventEmitter
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperHeadlessModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface {

    private var reactHost: ReactHost? = null
    private var reactNativeHost: ReactNativeHost? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME, hsConfig)

    @Volatile internal var prefetchedData: ReadableMap? = null
    @Volatile internal var sessionConfig: PaymentSessionConfiguration? = null

    /**
     * Runs the prefetch headless task and waits for its result.
     *
     * A prefetch miss is not fatal: the sheet and headless flows fall back to making the API
     * calls themselves, so this reports the failure and returns rather than propagating it.
     * The timeout matches the JS-side fallbacks so a wedged bridge can't stall the merchant.
     */
    suspend fun fetchPrefetch(
        taskSessionConfig: PaymentSessionConfiguration,
        headlessType: String = "prefetch",
    ): Result<ReadableMap> {
        val sdkAuthorization = taskSessionConfig.sdkAuthorization
        if (sdkAuthorization.isEmpty()) {
            return Result.failure(IllegalArgumentException("sdkAuthorization must not be empty"))
        }

        val prefetch = CompletableDeferred<ReadableMap>()
        if (HyperHeadlessModule.inFlightPrefetches.putIfAbsent(sdkAuthorization, prefetch) != null) {
            return Result.failure(
                IllegalStateException("A prefetch is already running for this sdkAuthorization")
            )
        }
        launchHeadlessTask(
            configuration = null,
            headlessType = headlessType,
            taskSessionConfig = taskSessionConfig,
        )

        val data = withTimeoutOrNull(PREFETCH_TIMEOUT_MS) { prefetch.await() }
        if (data == null) {
            // Remove only this exact deferred so a newer prefetch cannot be cleared by this one.
            HyperHeadlessModule.inFlightPrefetches.remove(sdkAuthorization, prefetch)
            Log.w(TAG, "Prefetch timed out after ${PREFETCH_TIMEOUT_MS}ms; falling back to on-demand API calls")
            return Result.failure(IllegalStateException("Prefetch timed out"))
        }

        return Result.success(data)
    }

    fun commitPrefetch(
        committedSessionConfig: PaymentSessionConfiguration,
        data: ReadableMap?,
    ) {
        sessionConfig = committedSessionConfig
        prefetchedData = data
    }

    fun clearPrefetch(sdkAuthorization: String) {
        if (sessionConfig?.sdkAuthorization == sdkAuthorization) {
            prefetchedData = null
        }
        emitPrefetchCacheRemoval(sdkAuthorization)
    }

    private fun emitPrefetchCacheRemoval(sdkAuthorization: String) {
        if (sdkAuthorization.isEmpty()) return
        val emit = { context: ReactContext ->
            context
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(
                    PREFETCH_CACHE_REMOVAL_EVENT,
                    Arguments.createMap().apply {
                        putString("sdkAuthorization", sdkAuthorization)
                    }
                )
        }
        activity.runOnUiThread {
            currentReactContext()?.let(emit)
        }
    }

    @SuppressLint("VisibleForTests")
    override fun initializeReactNativeInstance() {
        try {
            // Get ReactNativeHost from ReactNativeController singleton instead of casting Application to ReactApplication
            // This allows merchants to use their own Application class without extending MainApplication
            if (!ReactNativeController.getIsInitialized()){
                ReactNativeController.initialize(activity.application)
            }
            reactNativeHost = ReactNativeController.getReactNativeHost()
            reactHost = ReactNativeController.getReactHost()

            if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
                checkNotNull(reactHost) { "ReactHost is not initialized in New Architecture" }
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

    /**
     * The context can be null on first launch, so always read the host's live context. Later
     * tasks then reuse the runtime created by the first task instead of initializing it again.
     */
    private fun currentReactContext(): ReactContext? {
        val context = if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            reactHost?.currentReactContext
        } else {
            reactNativeHost?.reactInstanceManager?.currentReactContext
        }
        return context
    }

    override fun startHeadlessTask(
        configuration: SavedPaymentMethodsConfiguration?,
        headlessType: String
    ) {
        launchHeadlessTask(configuration, headlessType, sessionConfig)
    }

    private fun launchHeadlessTask(
        configuration: SavedPaymentMethodsConfiguration?,
        headlessType: String,
        taskSessionConfig: PaymentSessionConfiguration?,
    ) {
        activity.runOnUiThread {
            val context = currentReactContext()
            if (context == null) {
                if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
                    val reactHost = checkNotNull(reactHost)
                    reactHost.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                reactHost.removeReactInstanceEventListener(this)
                                invokeStartTask(
                                    context,
                                    configuration,
                                    headlessType,
                                    taskSessionConfig,
                                )
                            }
                        }
                    )
                    reactHost.start()
                } else {
                    val reactInstanceManager = reactNativeHost?.reactInstanceManager
                    reactInstanceManager?.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                reactInstanceManager.removeReactInstanceEventListener(this)
                                invokeStartTask(
                                    context,
                                    configuration,
                                    headlessType,
                                    taskSessionConfig,
                                )
                            }
                        }
                    )
                    reactInstanceManager?.createReactContextInBackground()
                }
            } else {
                invokeStartTask(context, configuration, headlessType, taskSessionConfig)
            }
        }
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { HyperEventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

    private fun invokeStartTask(
        reactContext: ReactContext,
        configuration: SavedPaymentMethodsConfiguration? = null,
        headlessType: String = "savedPM",
        taskSessionConfig: PaymentSessionConfiguration? = sessionConfig,
    ) {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundle(
            reactContext,
            taskSessionConfig,
            null,
            subscribedEvents,
        )
        bundle.getBundle("props")?.putString("headlessType", headlessType)
        configuration?.let { config ->
            bundle.getBundle("props")?.putBundle("configuration", config.bundle)
        }
        val taskTimeout = when (headlessType) {
            "prefetch", "updateIntent" -> PREFETCH_TASK_TIMEOUT_MS
            // Saved-method confirmation resumes through the callback held by this task and can
            // legitimately wait for merchant/user input. Zero is RN's explicit no-timeout mode.
            else -> SAVED_METHODS_TASK_TIMEOUT_MS
        }
        val taskConfig = HeadlessJsTaskConfig(
            "HyperHeadless", Arguments.fromBundle(bundle), taskTimeout, true, null
        )

        val headlessJsTaskContext = HeadlessJsTaskContext.getInstance(reactContext)
        UiThreadUtil.runOnUiThread {
            headlessJsTaskContext.startTask(taskConfig)
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
        if (data != null) {
            propsBundle.putBundle(
                "prefetchedApiData",
                launchOptions.toBundle(data.toHashMap())
            )
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

    private companion object {
        const val TAG = "HyperPrefetch"
        const val PREFETCH_CACHE_REMOVAL_EVENT = "clearPrefetchCache"

        /** Matches the JS-side fallback budget so neither side waits on the other. */
        const val PREFETCH_TIMEOUT_MS = 30_000L
        const val PREFETCH_TASK_TIMEOUT_MS = PREFETCH_TIMEOUT_MS + 1_000L
        const val SAVED_METHODS_TASK_TIMEOUT_MS = 0L
    }
}
