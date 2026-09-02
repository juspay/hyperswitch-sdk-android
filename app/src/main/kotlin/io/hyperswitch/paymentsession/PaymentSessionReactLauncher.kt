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
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperHeadlessModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface {

    private var reactHost: ReactHost? = null
    private var reactContext: ReactContext? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME, hsConfig)

    @Volatile override var sessionConfig: PaymentSessionConfiguration? = null

    /** Thrown when the same sdkAuthorization is being fetched in another in-progress session:
     *  the in-flight caller owns the entry — a duplicate must neither clear nor commit. */
    internal class DuplicateSessionInitException(sdkAuthorization: String) : IllegalStateException(
        "sdkAuthorization '$sdkAuthorization' is already in use by an in-progress session"
    ) {
        init {
            initCause(Throwable("SESSION_INIT_IN_PROGRESS"))
        }
    }

    /**
     * Runs the prefetch headless task and waits for its result.
     *
     * A prefetch miss is not fatal: the sheet and headless flows fall back to making the API
     * calls themselves, so this reports the failure and returns rather than propagating it.
     * The timeout is the SDK-side last resort: JS has no budget of its own, so a wedged
     * bridge must not stall the merchant.
     */
    internal suspend fun fetchPrefetch(
        taskSessionConfig: PaymentSessionConfiguration,
        headlessType: String = "prefetch",
    ): Result<ReadableMap> {
        val sdkAuthorization = taskSessionConfig.sdkAuthorization
        if (sdkAuthorization.isEmpty()) {
            return Result.failure(IllegalArgumentException("sdkAuthorization must not be empty"))
        }

        val prefetch = CompletableDeferred<ReadableMap>()
        if (HyperHeadlessModule.inFlightPrefetches.putIfAbsent(sdkAuthorization, prefetch) != null) {
            throw DuplicateSessionInitException(sdkAuthorization)
        }
        launchHeadlessTask(
            configuration = null,
            headlessType = headlessType,
            taskSessionConfig = taskSessionConfig,
        )

        // The deferred only signals completion: JS cached the payload in its own module
        // state (shared VM) before calling completePrefetch, and resolves it from there.
        val data = withTimeoutOrNull(PREFETCH_TIMEOUT_MS) { prefetch.await() }
        if (data == null) {
            // Remove only this exact deferred so a newer prefetch cannot be cleared by this one.
            HyperHeadlessModule.inFlightPrefetches.remove(sdkAuthorization, prefetch)
            Log.w(TAG, "Prefetch timed out after ${PREFETCH_TIMEOUT_MS}ms; falling back to on-demand API calls")
            return Result.failure(IllegalStateException("Prefetch timed out"))
        }

        return Result.success(data)
    }

    fun commitSession(committedSessionConfig: PaymentSessionConfiguration) {
        sessionConfig = committedSessionConfig
    }

    fun clearPrefetch(sdkAuthorization: String) {
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
        reactContext = try {
            // This allows merchants to use their own Application class without extending MainApplication
            if (!ReactNativeController.getIsInitialized()){
                ReactNativeController.initialize(activity.application)
            }
            reactHost = ReactNativeController.getReactHost()

            checkNotNull(reactHost) { "ReactHost is not initialized" }.currentReactContext
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

    private fun currentReactContext(): ReactContext? = reactHost?.currentReactContext

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
                val reactHost = checkNotNull(reactHost)
                reactHost.addReactInstanceEventListener(
                    object : ReactInstanceEventListener {
                        override fun onReactContextInitialized(context: ReactContext) {
                            invokeStartTask(context, configuration, headlessType, taskSessionConfig)
                            reactHost.removeReactInstanceEventListener(this)
                        }
                    }
                )
                reactHost.start()
            } else {
                invokeStartTask(context, configuration, headlessType, taskSessionConfig)
            }
        }
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { ReactNativeController.eventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

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
        applyFonts(configuration, bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundleWithHyperParams(configurationMap, subscribedEvents)
        return presentSheet(bottomInsetToDIPFromPixel(bundle))
    }

    private fun presentSheet(bundle: Bundle): Boolean {
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            val newReactNativeFragmentSheet =
                HyperFragment.Builder().setComponentName("hyperSwitch").setLaunchOptions(bundle)
                    .setFabricEnabled(true).build()

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

        /** SDK-side last-resort budget; JS has no budget of its own. */
        const val PREFETCH_TIMEOUT_MS = 30_000L
        const val PREFETCH_TASK_TIMEOUT_MS = PREFETCH_TIMEOUT_MS + 1_000L
        const val SAVED_METHODS_TASK_TIMEOUT_MS = 0L
    }
}
