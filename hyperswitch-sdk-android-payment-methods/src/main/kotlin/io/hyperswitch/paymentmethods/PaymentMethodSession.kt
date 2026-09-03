package io.hyperswitch.paymentmethods

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import com.facebook.react.ReactHost
import io.hyperswitch.model.HyperswitchBaseConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A payment-method session (`pmsInstance`) created via
 * `HyperswitchInstance.initPaymentMethodSession(sdkAuthorization, configuration)`.
 *
 * Every instance owns a **separate React Native host** (see
 * [PaymentMethodSessionReactHostProvider]) — two sessions never share a JS runtime,
 * and neither shares the main payment SDK's host.
 *
 * Follow the [io.hyperswitch.sdk.PaymentSession] pattern: create one session per
 * `sdkAuthorization` and bind UI widgets through [createCardForm].
 */
class PaymentMethodSession internal constructor(
    internal val activity: Activity,
    val sdkAuthorization: String,
    val configuration: PaymentMethodSessionConfiguration,
    configurationDeferred: Deferred<HyperswitchBaseConfiguration?>? = null,
) {
    private val application: Application = activity.application

    private val hostProvider = PaymentMethodSessionReactHostProvider(application)

    /** Unique id of this session's dedicated React host — distinct for every session. */
    val hostInstanceId: Int
        get() = hostProvider.hostInstanceId

    /** This session's dedicated React host — created on first use, never shared. */
    internal val reactHost: ReactHost
        get() = hostProvider.reactHost

    @Volatile
    private var hsConfig: HyperswitchBaseConfiguration? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        configurationDeferred?.let { deferred ->
            scope.launch { hsConfig = deferred.await() }
        }
    }

    /**
     * Creates a [CardForm] instance backed by an empty ("headless") RN view
     * on this session's React host.
     */
    fun createCardForm(): CardForm {
        runCatching { reactHost.onHostResume(activity) }
            .onFailure { Log.w(TAG, "Failed to resume React host: ${it.message}") }
        return CardForm(this)
    }

    /**
     * Session payload handed to every RN surface of this session:
     * `session = { sdk_auth = ..., vault_type = ..., vault_data = ... }`.
     */
    internal fun sessionBundle(): Bundle = Bundle().apply {
        putString("sdk_auth", sdkAuthorization)
        putAll(configuration.toBundle())
    }

    /**
     * Builds the full launch options for an RN surface owned by this session:
     * `{ props = { type, configuration, session, hyperswitchConfig, sdkParams, from } }`.
     */
    internal fun buildLaunchOptions(type: String, configuration: Bundle?): Bundle =
        Bundle().apply {
            putBundle("props", Bundle().apply {
                putString("type", type)
                putString("from", FROM)
                configuration?.let { putBundle("configuration", it) }
                putBundle("session", sessionBundle())
                hsConfig?.let { putBundle("hyperswitchConfig", it.toBundle()) }
                putBundle("sdkParams", buildSdkParams())
            })
        }

    private fun buildSdkParams(): Bundle = Bundle().apply {
        putString("appId", application.packageName)
        putString("country", application.resources.configuration.locales.get(0)?.country)
        putString("user-agent", resolveUserAgent())
        putDouble("launchTime", System.currentTimeMillis().toDouble())
        putString("sdkVersion", io.hyperswitch.BuildConfig.VERSION_NAME)
        putString("device_model", Build.MODEL)
        putString("os_type", "android")
        putString("os_version", Build.VERSION.RELEASE)
        putString("deviceBrand", Build.BRAND)
    }

    private fun resolveUserAgent(): String = try {
        WebSettings.getDefaultUserAgent(application)
    } catch (_: RuntimeException) {
        System.getProperty("http.agent") ?: ""
    }

    /** Stops the session's React host and releases all native resources held by it. */
    fun release() {
        scope.cancel()
        runCatching { reactHost.destroy("PaymentMethodSession released", null) }
            .onFailure { Log.w(TAG, "Failed to destroy React host: ${it.message}") }
    }

    internal companion object {
        private const val TAG = "PaymentMethodSession"
        private const val FROM = "nativeWidget"
    }
}
