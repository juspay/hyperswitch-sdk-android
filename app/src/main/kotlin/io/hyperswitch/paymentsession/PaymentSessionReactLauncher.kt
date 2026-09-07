package io.hyperswitch.paymentsession

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperReactRuntime
import io.hyperswitch.react.ReactNativeController
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface {

    override var sessionConfig: PaymentSessionConfiguration? = null

    /** This session's own React host, emitter and router. Created in [initializeReactNativeInstance]. */
    internal lateinit var runtime: HyperReactRuntime
        private set

    private var prefetchSurface: HeadlessSurface? = null
    private var savedPaymentMethodsSurface: HeadlessSurface? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME, hsConfig)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** One updateIntent in flight; identity-checked on finish so a stale timeout cannot end a later attempt. */
    private class UpdateIntentAttempt(val onResult: (Result<String>) -> Unit) {
        var authorization: String? = null
        var timeout: Runnable? = null
    }

    private var updateIntentAttempt: UpdateIntentAttempt? = null

    @SuppressLint("VisibleForTests")
    override fun initializeReactNativeInstance() {
        try {
            // Allows merchants to use their own Application class without extending MainApplication.
            ReactNativeController.initialize(activity.application)
            runtime = HyperReactRuntime(activity.application).also { rt ->
                rt.onPrefetchUpdateIntentReply = { type, resultJson ->
                    mainHandler.post { handleUpdateIntentReply(type, resultJson) }
                }
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

    // ── Prefetch surface ──────────────────────────────────────────────────────

    /** Viewless prefetch surface; prerenderSurface starts the instance itself if needed. */
    override fun prefetch() {
        val config = sessionConfig ?: return

        activity.runOnUiThread {
            if (prefetchSurface != null) return@runOnUiThread
            val props = bottomInsetToDIPFromPixel(
                launchOptions.getBundle(
                    activity.applicationContext,
                    config,
                    null,
                    getSubscribedEventsSafely(),
                )
            )
            props.getBundle("props")?.apply {
                // getBundle hardcodes type=payment.
                putString("type", "prefetch")
                putInt("prefetchTag", HyperReactRuntime.PREFETCH_SURFACE_TAG)
            }
            prefetchSurface = HeadlessSurface(activity.applicationContext, runtime.reactHost)
                .also { it.start(props) }
        }
    }

    /** Resolves once this session's JS realm is initialised. Awaits the realm, never the data. */
    override suspend fun awaitReady() {
        val host = runtime.reactHost
        if (host.currentReactContext != null) return
        suspendCancellableCoroutine { continuation ->
            val listener = object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) {
                    host.removeReactInstanceEventListener(this)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            host.addReactInstanceEventListener(listener)
            continuation.invokeOnCancellation { host.removeReactInstanceEventListener(listener) }
            host.start()
        }
    }

    override fun disposePrefetch() {
        activity.runOnUiThread {
            prefetchSurface?.stop()
            prefetchSurface = null
        }
    }

    // ── Update intent ─────────────────────────────────────────────────────────

    /**
     * Tells the prefetch surface an update has begun (overlay), asks the merchant for the
     * new authorization, then has the surface refetch and fan out in JS. Only the refetch
     * leg is timed out; the merchant's provider is their own. Config commits on success.
     */
    override fun updateIntent(
        authorizationProvider: (onAuthorization: (String) -> Unit) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        mainHandler.post {
            if (updateIntentAttempt != null) {
                onResult(Result.failure(failure("ALREADY_IN_PROGRESS", "updateIntent already in progress")))
                return@post
            }
            val attempt = UpdateIntentAttempt(onResult)
            updateIntentAttempt = attempt
            prefetch()
            emitToPrefetchSurface("updateIntentInit", null)

            authorizationProvider { auth ->
                mainHandler.post { refetch(auth, attempt) }
            }
        }
    }

    private fun refetch(auth: String, attempt: UpdateIntentAttempt) {
        if (updateIntentAttempt !== attempt) return
        if (auth.isEmpty()) {
            finish(attempt, Result.failure(failure("INVALID_SDK_AUTHORIZATION", "No sdkAuthorization was provided")))
            return
        }
        attempt.authorization = auth

        val timeout = Runnable {
            finish(attempt, Result.failure(failure("UPDATE_INTENT_TIMEOUT", "The updated payment intent was not acknowledged in time.")))
        }
        attempt.timeout = timeout
        mainHandler.postDelayed(timeout, UPDATE_INTENT_TIMEOUT_MS)

        emitToPrefetchSurface("updateIntentComplete", auth)
    }

    private fun handleUpdateIntentReply(type: String, resultJson: String) {
        if (type != "UPDATE_INTENT_COMPLETE_RETURNED") return
        val attempt = updateIntentAttempt ?: return
        val auth = attempt.authorization ?: ""
        val result = parseUpdateIntentResult(resultJson, auth)
        if (result.isSuccess) {
            sessionConfig = PaymentSessionConfiguration(auth)
        }
        finish(attempt, result)
    }

    private fun finish(attempt: UpdateIntentAttempt, result: Result<String>) {
        if (updateIntentAttempt !== attempt) return
        attempt.timeout?.let { mainHandler.removeCallbacks(it) }
        updateIntentAttempt = null
        attempt.onResult(result)
    }

    private fun emitToPrefetchSurface(name: String, sdkAuthorization: String?) {
        val payload = Arguments.createMap().apply {
            putInt("rootTag", HyperReactRuntime.PREFETCH_SURFACE_TAG)
            sdkAuthorization?.let { putString("sdkAuthorization", it) }
        }
        if (!runtime.eventEmitter.emitEvent(name, payload)) {
            Log.w("PaymentSessionReactLauncher", "emitToPrefetchSurface: HyperModule not attached, dropped $name")
        }
    }

    private fun parseUpdateIntentResult(json: String, auth: String): Result<String> = try {
        val obj = JSONObject(json)
        when (val status = obj.optString("status")) {
            "failed", "error", "cancelled" -> Result.failure(
                failure(
                    obj.optString("code").ifEmpty { "UNKNOWN_ERROR" },
                    obj.optString("message").ifEmpty { status },
                )
            )
            else -> Result.success(auth)
        }
    } catch (e: Exception) {
        Result.failure(failure("UNKNOWN_ERROR", "Invalid update intent result"))
    }

    private fun failure(code: String, message: String): Throwable =
        Throwable(message).apply { initCause(Throwable(code)) }

    // ── Saved payment methods ─────────────────────────────────────────────────

    /** Starts a HyperHeadless surface in saved-payment-methods mode; a new call replaces the previous one. */
    override fun recreateReactContext(configuration: SavedPaymentMethodsConfiguration?) {
        activity.runOnUiThread {
            val bundle = launchOptions.getBundle(
                activity.applicationContext,
                sessionConfig,
                null,
                getSubscribedEventsSafely(),
            )
            configuration?.let { config ->
                bundle.getBundle("props")?.putBundle("configuration", config.bundle)
            }
            savedPaymentMethodsSurface?.stop()
            savedPaymentMethodsSurface = HeadlessSurface(activity.applicationContext, runtime.reactHost)
                .also { it.start(bundle) }
        }
    }

    private fun getSubscribedEventsSafely(): List<String> =
        try { runtime.eventEmitter.getSubscribedEvents() } catch (_: Exception) { emptyList() }

    // ── Sheet ─────────────────────────────────────────────────────────────────

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
                    subscribedEvents
                )
            )
        )
    }

    private fun presentSheet(bundle: Bundle): Boolean {
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            val newReactNativeFragmentSheet =
                HyperFragment.Builder().setComponentName("hyperSwitch").setLaunchOptions(bundle)
                    .setFabricEnabled(true).build()
                    .also { it.runtime = runtime }

            val activity2 = activity as FragmentActivity

            activity2.onBackPressedDispatcher.addCallback {
                newReactNativeFragmentSheet.onBackPressed()
            }

            activity2.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, newReactNativeFragmentSheet, "paymentSheet")
                .commitAllowingStateLoss()

            return true
        } else {
            // Intents can't carry objects; HyperActivity takes this in onCreate.
            ReactNativeController.offerActivityRuntime(runtime)
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
        const val UPDATE_INTENT_TIMEOUT_MS = 30_000L
    }
}
