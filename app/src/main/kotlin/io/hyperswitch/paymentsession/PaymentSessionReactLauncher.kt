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
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperFragment
import kotlinx.coroutines.CancellationException
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

    /** The session's one long-running headless task; null when no task is expected to be live. */
    @Volatile private var headlessTaskId: Int? = null

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
        if (!ReactNativeController.sessionRouter.tryRegisterPrefetchCallback(prefetch)) {
            return Result.failure(
                IllegalStateException(
                    "sdkAuthorization '$sdkAuthorization' is already in use by an in-progress session"
                ).apply { initCause(Throwable("SESSION_INIT_IN_PROGRESS")) }
            )
        }
        launchHeadlessTask(
            configuration = null,
            headlessType = headlessType,
            taskSessionConfig = taskSessionConfig,
        )

        // The deferred only signals completion: JS cached the payload in its own module
        // state (shared VM) before calling completePrefetch, and resolves it from there.
        val data = try {
            withTimeoutOrNull(PREFETCH_TIMEOUT_MS) { prefetch.await() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            /* routeLaunchFailure completed the deferred exceptionally: report it the same way
               a timeout is reported so initPaymentSession has one failure channel. */
            return Result.failure(error)
        } finally {
            /* Every exit — success, timeout, failure, or a cancelled parent coroutine —
               must free the waiter. clearPrefetchCallback only clears this exact deferred,
               so a new registration can never be clobbered by a cancelling waiter. */
            ReactNativeController.sessionRouter.clearPrefetchCallback(prefetch)
        }
        if (data == null) {
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
            try {
                val context = currentReactContext()
                if (context == null) {
                    val reactHost = checkNotNull(reactHost)
                    reactHost.addReactInstanceEventListener(
                        object : ReactInstanceEventListener {
                            override fun onReactContextInitialized(context: ReactContext) {
                                try {
                                    dispatch(context, buildHeadlessProps(context, configuration, headlessType, taskSessionConfig))
                                } catch (error: Throwable) {
                                    routeLaunchFailure(error)
                                }
                                reactHost.removeReactInstanceEventListener(this)
                            }
                        }
                    )
                    reactHost.start()
                } else {
                    dispatch(context, buildHeadlessProps(context, configuration, headlessType, taskSessionConfig))
                }
            } catch (error: Throwable) {
                routeLaunchFailure(error)
            }
        }
    }

    /* Anything thrown inside the posted runnable escapes the callers' try/catch around
       startHeadlessTask — fail the registered waiter instead of crashing the app. */
    private fun routeLaunchFailure(error: Throwable) {
        Log.e(TAG, "Headless task launch failed", error)
        ReactNativeController.sessionRouter.failPrefetchCallback(error)
        ReactNativeController.sessionRouter.executeSessionCallback(
            PaymentSessionHandlerImpl.failed(error, ReactNativeController.sessionRouter)
        )
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { ReactNativeController.eventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

    /* Rebuilds the props map from LaunchOptions per request, so sdkParams, subscribedEvents and
       configuration are always current — the headlessRequest event carries exactly this map. */
    private fun buildHeadlessProps(
        reactContext: ReactContext,
        configuration: SavedPaymentMethodsConfiguration? = null,
        headlessType: String = "savedPM",
        taskSessionConfig: PaymentSessionConfiguration? = sessionConfig,
    ): WritableMap {
        val subscribedEvents = getSubscribedEventsSafely()
        val bundle = launchOptions.getBundle(
            reactContext,
            taskSessionConfig,
            null,
            subscribedEvents,
        )
        bundle.getBundle("props")!!.putString("headlessType", headlessType)
        configuration?.let { config ->
            bundle.getBundle("props")!!.putBundle("configuration", config.bundle)
        }
        return Arguments.fromBundle(bundle.getBundle("props")!!)
    }

    /* One task per session: after the first startTask everything is an event into the live
       JS closure. Liveness comes from RN, not emitEvent: emitEvent returns true for any
       attached HyperModule, and after a ReactHost restart the module re-attaches long before
       the new runtime subscribes — the event would fall on the floor and the merchant's
       callback would hang. HeadlessJsTaskContext is per-ReactContext, so a stale id from the
       old runtime reports not-running and a dead runtime self-heals into a cold start —
       the same path a savedPM request takes on main. */
    private fun dispatch(reactContext: ReactContext, props: WritableMap) {
        val live = headlessTaskId?.let {
            HeadlessJsTaskContext.getInstance(reactContext).isTaskRunning(it)
        } == true
        if (live && ReactNativeController.eventEmitter.emitEvent(HEADLESS_REQUEST_EVENT, props)) return
        headlessTaskId = null
        startHeadlessJsTask(reactContext, props)
    }

    /* Callers are always on the UI thread (runOnUiThread block / onReactContextInitialized):
       assign inline — a posted hop would defer headlessTaskId for a main-loop iteration. */
    private fun startHeadlessJsTask(reactContext: ReactContext, props: WritableMap) {
        val taskProps = Arguments.createMap().apply { putMap("props", props) }
        /* Timeout 0 is RN's explicit no-timeout mode: the task outlives its first request by
           design (it can wait for merchant/user input). The 30s budget lives in fetchPrefetch. */
        val taskConfig = HeadlessJsTaskConfig("HyperHeadless", taskProps, 0, true, null)
        headlessTaskId = HeadlessJsTaskContext.getInstance(reactContext).startTask(taskConfig)
    }

    /** Ends the session's task. finishTask ends it on its own: the task's JS promise never
        resolves, which is inert (AppRegistry only forwards settlement to notifyTaskFinished).
        No shutdown event: emitting one would race the next session's startTask through two
        cross-thread enqueues onto the JS queue, and the subscription is runtime-lifetime
        module state, not per-task state. */
    internal fun finishHeadlessTask() {
        val taskId = headlessTaskId ?: return
        headlessTaskId = null
        val reactContext = currentReactContext() ?: return
        UiThreadUtil.runOnUiThread {
            runCatching { HeadlessJsTaskContext.getInstance(reactContext).finishTask(taskId) }
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

        /** SDK-side last-resort budget; JS has no budget of its own. */
        const val PREFETCH_TIMEOUT_MS = 30_000L
    }
}

/* Codegen event channel only: JS subscribes through NativeHyperModule.clearPrefetchCache,
   which never sees RCTDeviceEventEmitter traffic. Same path iOS uses. Shared by the
   launcher's clearPrefetch and every PaymentSessionHandlerImpl terminal result. */
private const val PREFETCH_CACHE_REMOVAL_EVENT = "clearPrefetchCache"
private const val HEADLESS_REQUEST_EVENT = "headlessRequest"

internal fun emitPrefetchCacheRemoval(sdkAuthorization: String) {
    if (sdkAuthorization.isEmpty()) return
    ReactNativeController.eventEmitter.emitEvent(
        PREFETCH_CACHE_REMOVAL_EVENT,
        Arguments.createMap().apply {
            putString("sdkAuthorization", sdkAuthorization)
        },
    )
}
