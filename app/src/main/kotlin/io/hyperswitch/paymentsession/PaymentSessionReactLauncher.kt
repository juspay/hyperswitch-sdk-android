package io.hyperswitch.paymentsession

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.BuildConfig
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperActivity
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperReactRuntime
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.react.SDK_INIT_FAILED
import io.hyperswitch.react.UpdateIntentReplyTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One PaymentSession's surfaces on the shared React host. The session owns a
 * prefetch surface, whose root tag is the session's identity in JS, and at most
 * one saved-payment-methods surface. Replies from either come back by root tag.
 */
class PaymentSessionReactLauncher(
    private val activity: Activity,
    hsConfig: HyperswitchBaseConfiguration? = null,
) : SDKInterface, UpdateIntentReplyTarget {

    override var sessionConfig: PaymentSessionConfiguration? = null

    /** The running prefetch surface and the props it currently renders. */
    private class Prefetch(val surface: HeadlessSurface, val props: Bundle)

    /** Written on the main thread; read from any thread through [sessionTag]. */
    @Volatile
    private var prefetch: Prefetch? = null
    private val prefetchSurface: HeadlessSurface?
        get() = prefetch?.surface

    /** Set by [close]: no surface is started again for this session. */
    @Volatile
    private var closed = false

    /** Numbers updateIntent calls so the surface can tell consecutive ones apart. */
    private var updateIntentSequence = 0
    private var savedPaymentMethods: Pair<HeadlessSurface, HeadlessAttempt>? = null
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME, hsConfig)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val runtime: HyperReactRuntime
        get() = ReactNativeController.runtime

    /** Whether the payments host could start; nothing is shown or awaited once it could not. */
    private val health
        get() = ReactNativeController.health

    /** Root tag of the prefetch surface: the session's identity in JS. Null until prefetched. */
    override val sessionTag: Int?
        get() = prefetchSurface?.rootTag

    /** One updateIntent in flight; identity-checked on finish so a stale terminal event cannot end a later attempt. */
    private class UpdateIntentAttempt(val onResult: (Result<String>) -> Unit) {
        var authorization: String? = null
    }

    @Volatile
    private var updateIntentAttempt: UpdateIntentAttempt? = null

    /** True between `updateIntent` being called and its result; confirms are refused meanwhile. */
    val isUpdatingIntent: Boolean
        get() = updateIntentAttempt != null

    override fun initializeReactNativeInstance() {
        try {
            // Allows merchants to use their own Application class without extending MainApplication.
            ReactNativeController.initialize(activity.application)
            // The host runs this session's JS only while its Activity is resumed.
            runtime.follow(activity)
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

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    // ── Prefetch surface ──────────────────────────────────────────────────────

    /**
     * Main thread. Starts the prefetch surface once, under the current [sessionConfig]. Its
     * root tag is the session's identity for every other surface, so it is never replaced:
     * changed credentials reach it through its props. Null before [initPaymentSession] and
     * after [close].
     */
    private fun ensurePrefetch(): HeadlessSurface? {
        prefetch?.let { return it.surface }
        if (closed || health.initFailure != null) return null
        val config = sessionConfig ?: return null
        val props = bottomInsetToDIPFromPixel(
            launchOptions.getBundle(activity.applicationContext, config, null, emptyList())
        )
        // getBundle hardcodes type=payment.
        props.getBundle("props")?.putString("type", "prefetch")
        return HeadlessSurface.start(activity.applicationContext, runtime.reactHost, props, owner = this)
            .also { prefetch = Prefetch(it, props) }
    }

    /**
     * Starts the prefetch surface, or moves a running one to the current [sessionConfig]:
     * React re-renders it under the new credentials and JS fetches for them.
     */
    override fun prefetch() {
        runOnMain {
            val current = prefetch
            if (current == null) {
                ensurePrefetch()
                return@runOnMain
            }
            val config = sessionConfig ?: return@runOnMain
            failPendingUpdateIntent("SESSION_REINITIALISED", "initPaymentSession replaced the intent while updateIntent was in flight")
            updateIntentSequence += 1
            pushUpdateIntent("complete", config)
            savedPaymentMethods?.second?.sdkAuthorization = config.sdkAuthorization
        }
    }

    /**
     * Resolves once the prefetch surface is running, which is when its data is on its way.
     * Never throws: a host that cannot start fails [health] instead, and the session then
     * answers every call with SDK_INIT_FAILED.
     */
    override suspend fun awaitReady() {
        val surface = withContext(Dispatchers.Main.immediate) { ensurePrefetch() } ?: return
        try {
            surface.awaitStarted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            health.fail("the payment session could not start", e)
        }
    }

    // ── Update intent ─────────────────────────────────────────────────────────

    /**
     * Drives the prefetch surface through its props, not events: `init` marks the update
     * (overlay on the session's other surfaces), asks the merchant for the new authorization,
     * then `complete` carries the new credentials and the surface refetches and fans out in
     * JS. There is no timer: the attempt ends with the JS reply, or with the native event
     * that rules a reply out (the session closed or re-initialised). Config commits on success.
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
            if (ensurePrefetch() == null) {
                val initFailure = health.initFailure
                val (code, message) = when {
                    closed -> "SESSION_CLOSED" to "The payment session was closed"
                    initFailure != null -> SDK_INIT_FAILED to initFailure.message.orEmpty()
                    else -> "NOT_INITIALISED" to "initPaymentSession has not been called"
                }
                onResult(Result.failure(failure(code, message)))
                return@post
            }
            val attempt = UpdateIntentAttempt(onResult)
            updateIntentAttempt = attempt
            updateIntentSequence += 1
            pushUpdateIntent("init", configuration = null)

            authorizationProvider { auth ->
                mainHandler.post { refetch(auth, attempt) }
            }
        }
    }

    private fun refetch(auth: String, attempt: UpdateIntentAttempt) {
        if (updateIntentAttempt !== attempt) return
        if (auth.isEmpty()) {
            // The overlay went up on `init`; JS lowers it on `cancel`.
            pushUpdateIntent("cancel", configuration = null)
            finish(attempt, Result.failure(failure("INVALID_SDK_AUTHORIZATION", "No sdkAuthorization was provided")))
            return
        }
        if (prefetch == null) {
            finish(attempt, Result.failure(failure("SESSION_CLOSED", "The payment session was closed")))
            return
        }
        attempt.authorization = auth

        pushUpdateIntent("complete", PaymentSessionConfiguration(auth))
    }

    /**
     * Main thread. Re-renders the prefetch root with an `updateIntent` marker and, for
     * `complete`, the new `paymentSessionConfig`. The surface then carries the attempted
     * credentials whatever the outcome, as JS switched to them; [sessionConfig] only commits
     * on success.
     *
     * Mutating the retained bundle is safe: React never keeps a reference to it. The surface
     * constructor and [HeadlessSurface.updateProps] both deep-copy it into a NativeMap
     * (`Arguments.fromBundle`) synchronously on the calling thread, and every caller of this
     * function runs on the main thread, so nothing reads the bundle in between.
     */
    private fun pushUpdateIntent(phase: String, configuration: PaymentSessionConfiguration?) {
        val current = prefetch ?: return
        val inner = current.props.getBundle("props") ?: return
        configuration?.let { inner.putBundle("paymentSessionConfig", it.toBundle()) }
        inner.putBundle("updateIntent", Bundle().apply {
            putInt("attempt", updateIntentSequence)
            putString("phase", phase)
        })
        current.surface.updateProps(current.props)
    }

    /** JS reply, routed here because this launcher owns the prefetch surface it came from. */
    override fun onUpdateIntentReply(eventType: String, resultJson: String) {
        mainHandler.post { handleUpdateIntentReply(eventType, resultJson) }
    }

    private fun handleUpdateIntentReply(type: String, resultJson: String) {
        if (type != "UPDATE_INTENT_COMPLETE_RETURNED") return
        val attempt = updateIntentAttempt ?: return
        val auth = attempt.authorization ?: ""
        val result = parseUpdateIntentResult(resultJson, auth)
        if (result.isSuccess) {
            sessionConfig = PaymentSessionConfiguration(auth)
            savedPaymentMethods?.second?.sdkAuthorization = auth
        }
        finish(attempt, result)
    }

    private fun finish(attempt: UpdateIntentAttempt, result: Result<String>) {
        if (updateIntentAttempt !== attempt) return
        updateIntentAttempt = null
        attempt.onResult(result)
    }

    /**
     * Ends the attempt in flight once native knows its JS reply can no longer arrive, and
     * tells the surface so the session's other surfaces lower their overlay.
     */
    private fun failPendingUpdateIntent(code: String, message: String) {
        updateIntentAttempt?.let {
            pushUpdateIntent("cancel", configuration = null)
            finish(it, Result.failure(failure(code, message)))
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

    /**
     * Starts a HyperHeadless surface in saved-payment-methods mode. Its owner is a
     * [HeadlessAttempt] that delivers the handler and routes confirms; a new call
     * replaces the previous surface and fails whatever it still had pending.
     */
    override fun startSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration?,
        onHandler: (PaymentSessionHandler) -> Unit,
    ) {
        runOnMain {
            val initFailure = health.initFailure
            if (closed || initFailure != null) {
                val (code, reason) =
                    if (closed) "SESSION_CLOSED" to "The payment session was closed"
                    else SDK_INIT_FAILED to initFailure?.message.orEmpty()
                val failure = Arguments.createMap().apply {
                    putString("code", code)
                    putString("message", reason)
                }
                val refused = HeadlessAttempt("", onHandler).apply {
                    refuseConfirms(PaymentResult.Failed(Throwable(reason).apply { initCause(Throwable(code)) }))
                }
                onHandler(PaymentSessionHandlerImpl(refused, failure, failure, Arguments.createArray()))
                return@runOnMain
            }
            val bundle = launchOptions.getBundle(
                activity.applicationContext,
                sessionConfig,
                null,
                emptyList(),
            )
            bundle.getBundle("props")?.apply {
                // getBundle hardcodes type=payment.
                putString("type", "headless")
                configuration?.let { putBundle("configuration", it.bundle) }
            }
            stampSessionTag(bundle)
            val attempt = HeadlessAttempt(
                sessionConfig?.sdkAuthorization ?: "",
                onHandler,
                updating = { updateIntentAttempt != null },
            )
            savedPaymentMethods?.let { (surface, previous) ->
                previous.cancel()
                surface.stop()
            }
            savedPaymentMethods = HeadlessSurface.start(
                activity.applicationContext, runtime.reactHost, bundle, attempt
            ) to attempt
        }
    }

    // ── Sheet ─────────────────────────────────────────────────────────────────

    override fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?
    ): Boolean = presentSheet(sessionConfig, configuration, emptyList(), null, null)

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean =
        presentSheet(configurationMap, emptyList(), null, null)

    override fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?,
        subscribedEvents: List<String>,
        eventListener: PaymentEventListener?,
        onResult: ((PaymentResult) -> Unit)?,
    ): Boolean {
        val bundle = launchOptions.getBundle(sessionConfig, configuration, subscribedEvents)
        applyFonts(configuration, bundle)
        return presentSheet(bottomInsetToDIPFromPixel(bundle), eventListener, onResult)
    }

    override fun presentSheet(
        configurationMap: Map<String, Any?>,
        subscribedEvents: List<String>,
        eventListener: PaymentEventListener?,
        onResult: ((PaymentResult) -> Unit)?,
    ): Boolean {
        val bundle = launchOptions.getBundleWithHyperParams(configurationMap, subscribedEvents)
        return presentSheet(bottomInsetToDIPFromPixel(bundle), eventListener, onResult)
    }

    private fun presentSheet(
        bundle: Bundle,
        eventListener: PaymentEventListener?,
        onResult: ((PaymentResult) -> Unit)?,
    ): Boolean {
        if (health.initFailure != null) {
            // Nothing to show: the sheet would never render.
            val result = PaymentResult.Failed(health.resultError())
            runOnMain { onResult?.invoke(result) }
            return false
        }
        stampSessionTag(bundle)
        if (activity is DefaultHardwareBackBtnHandler && activity is FragmentActivity) {
            // The fragment owns this presentation: its root view carries it, so the JS exit
            // call for this sheet's root tag lands on it and nowhere else.
            val fragment = HyperFragment.Builder()
                .setComponentName("hyperSwitch")
                .setLaunchOptions(bundle)
                .setFabricEnabled(true)
                .build()
            onResult?.let { fragment.setOnPaymentResult(it) }
            eventListener?.let { fragment.setOnEventCallback(it) }

            // Scoped to the fragment, so the callback leaves with it.
            activity.onBackPressedDispatcher.addCallback(fragment) {
                fragment.onBackPressed()
            }

            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "paymentSheet")
                .commitAllowingStateLoss()

            return true
        } else {
            // An Intent cannot carry the completion; HyperActivity resolves through the
            // one-shot manager instead of an owner.
            onResult?.let { PaymentSheetCallbackManager.setCallback(it, false) }
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Stops every surface this session started and fails what is still waiting on them. */
    override fun close() {
        runOnMain {
            closed = true
            failPendingUpdateIntent("SESSION_CLOSED", "The payment session was closed")
            savedPaymentMethods?.let { (surface, attempt) ->
                attempt.cancel()
                surface.stop()
            }
            savedPaymentMethods = null
            prefetchSurface?.stop()
            prefetch = null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Surfaces of this session carry its tag so JS scopes session-wide events to it. */
    private fun stampSessionTag(bundle: Bundle) {
        val tag = prefetchSurface?.rootTag ?: return
        bundle.getBundle("props")?.getBundle("sdkParams")?.putInt("sessionTag", tag)
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
        const val TAG = "PaymentSessionReactLauncher"
    }
}
