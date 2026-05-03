package io.hyperswitch.paymentsession

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import org.json.JSONObject

class LaunchOptions(
    private val context: Context? = null,
    private val sdkVersion: String
) {

    private fun getHyperParams(): Bundle =
        Bundle().apply {
            putString("appId", context?.packageName)
            putString("country", context?.resources?.configuration?.locales?.get(0)?.country)
            putString("user-agent", getUserAgent(context))
            putDouble("launchTime", getCurrentTime())
            putString("sdkVersion", sdkVersion)
            putString("device_model", Build.MODEL)
            putString("os_type", "android")
            putString("os_version", Build.VERSION.RELEASE)
            putString("deviceBrand", Build.BRAND)
            val edgeInsets = getBottomInset(context)
            if(edgeInsets!=null) {
                putFloat("topInset", edgeInsets.top)
                putFloat("leftInset", edgeInsets.left)
                putFloat("rightInset", edgeInsets.right)
                putFloat("bottomInset", edgeInsets.bottom)
            }
        }

    private fun getHyperParamsMap(map: Map<*, *>): Map<*, *> =
        (map["hyperParams"] as? Map<*, *> ?: mutableMapOf<String, Any?>()).apply {
            plus(Pair("appId", context?.packageName))
            plus(Pair("country", context?.resources?.configuration?.locales?.get(0)?.country))
            plus(Pair("user-agent", getUserAgent(context)))
            plus(Pair("launchTime", getCurrentTime()))
            plus(Pair("sdkVersion", sdkVersion))
            plus(Pair("device_model", Build.MODEL))
            plus(Pair("os_type", "android"))
            plus(Pair("os_version", Build.VERSION.RELEASE))
            plus(Pair("deviceBrand",Build.BRAND))
            val edgeInsets = getBottomInset(context)
            if(edgeInsets!=null) {
                plus(Pair("topInset", edgeInsets.top))
                plus(Pair("leftInset", edgeInsets.left))
                plus(Pair("rightInset", edgeInsets.right))
                plus(Pair("bottomInset", edgeInsets.bottom))
            }
        }

    fun getBundle(
        sdkAuthorization: String,
        configuration: PaymentSheet.Configuration? = null,
        subscribedEvents: List<String> = emptyList()
    ): Bundle =
        context?.let { getBundle(it, sdkAuthorization, configuration, subscribedEvents) } ?: Bundle()

    fun getBundle(
        context: Context,
        sdkAuthorization: String,
        configuration: PaymentSheet.Configuration? = null,
        subscribedEvents: List<String> = emptyList()
    ): Bundle {
        val pubKey = PaymentConfiguration.getInstance(context).publishableKey
        val backendUrl = PaymentConfiguration.getInstance(context).customBackendUrl
        val logUrl = PaymentConfiguration.getInstance(context).customLogUrl
        val customParams = PaymentConfiguration.getInstance(context).customParams?.let {
            fromBundle(it) as Map<String, Any>
        }
        val theme = configuration?.appearance?.theme?.name

        return buildBundle(
            publishableKey = pubKey,
            sdkAuthorization = sdkAuthorization,
            configuration = configuration?.bundle,
            customBackendUrl = backendUrl,
            customLogUrl = logUrl,
            customParams = customParams,
            theme = theme,
            subscribedEvents = subscribedEvents,
            type = "payment",
            baseConfigurationBuilder = {
                putString("publishableKey", pubKey)
                backendUrl?.let { putString("overrideCustomBackendEndpoint", it) }
                logUrl?.let { putString("overrideCustomLoggingEndpoint", it) }
            }
        )
    }

    fun getBundle(
        publishableKey: String? = null,
        configuration: Bundle? = null,
        customBackendUrl: String? = null,
        customLogUrl: String? = null,
        customParams: Map<String, Any>? = null,
        type: String? = "payment",
        from: String? = "nativeWidget",
        sdkAuthorization : String? = null,
        subscribedEvents: List<String> = emptyList(),
    ): Bundle = buildBundle(
        publishableKey = publishableKey,
        sdkAuthorization = sdkAuthorization ?: "",
        configuration = configuration,
        customBackendUrl = customBackendUrl,
        customLogUrl = customLogUrl,
        customParams = customParams,
        theme = null,
        subscribedEvents = subscribedEvents,
        type = type,
        from = from,
        baseConfigurationBuilder = {
            putString("publishableKey", publishableKey ?: "")
            customBackendUrl?.let { putString("overrideCustomBackendEndpoint", it) }
            customLogUrl?.let { putString("overrideCustomLoggingEndpoint", it) }
        }
    )

    fun getBundleWithHyperParams(readableMap: Map<*, *>, subscribedEvents: List<String> = emptyList()): Bundle = Bundle().apply {
        putBundle("props", toBundle(readableMap).apply {
            putBundle("hyperParams", getHyperParams())
            putStringArrayList("subscribedEvents", ArrayList(subscribedEvents))
        })
    }

    /**
     * Common builder function for constructing the Bundle. All getBundle() variants delegate to this.
     */
    private fun buildBundle(
        publishableKey: String?,
        sdkAuthorization: String,
        configuration: Bundle?,
        customBackendUrl: String?,
        customLogUrl: String?,
        customParams: Map<String, Any>?,
        theme: String?,
        subscribedEvents: List<String>,
        type: String? = "payment",
        from: String? = null,
        baseConfigurationBuilder: (Bundle.() -> Unit)? = null
    ): Bundle = Bundle().apply {
        putBundle("props", Bundle().apply {
            putString("type", type)
            from?.let { putString("from", it) }
            putString("publishableKey", publishableKey ?: "")
            putString("sdkAuthorization", sdkAuthorization)

            val configCopy = configuration?.let { Bundle(it) }
            if (configCopy?.containsKey("hideConfirmButton") == false) {
                configCopy.putBoolean("hideConfirmButton", true)
            }
            putBundle("configuration", configCopy)

            val themeFromConfig = configCopy?.getBundle("appearance")?.getString("theme")
            putString("theme", themeFromConfig ?: theme)

            customBackendUrl?.let { putString("customBackendUrl", it) }
            customLogUrl?.let { putString("customLogUrl", it) }

            if (subscribedEvents.isNotEmpty()) {
                putStringArrayList("subscribedEvents", ArrayList(subscribedEvents))
            } else if (configCopy?.containsKey("subscribedEvents") == true) {
                val subscribedEventsArray = configCopy["subscribedEvents"] as? List<*>
                if (subscribedEventsArray != null) {
                    putSerializable("subscribedEvents", ArrayList(subscribedEventsArray))
                }
            }

            customParams?.let { putBundle("customParams", toBundle(it)) }
            putBundle("hyperParams", getHyperParams())
            putBundle("baseConfiguration", Bundle().apply {
                baseConfigurationBuilder?.invoke(this)
            })
        })
    }

    /**
     * Creates a baseConfiguration bundle from HyperswitchBaseConfiguration containing all endpoint overrides.
     */
    private fun getBaseConfigurationBundle(config: HyperswitchBaseConfiguration?): Bundle = Bundle().apply {
        config?.customConfig?.let { customConfig ->
            customConfig.customEndpoint?.let { putString("customEndpoint", it) }
            customConfig.overrideCustomBackendEndpoint?.let { putString("overrideCustomBackendEndpoint", it) }
            customConfig.overrideCustomAssetsEndpoint?.let { putString("overrideCustomAssetsEndpoint", it) }
            customConfig.overrideCustomSDKConfigEndpoint?.let { putString("overrideCustomSDKConfigEndpoint", it) }
            customConfig.overrideCustomConfirmEndpoint?.let { putString("overrideCustomConfirmEndpoint", it) }
            customConfig.overrideCustomAirborneEndpoint?.let { putString("overrideCustomAirborneEndpoint", it) }
            customConfig.overrideCustomLoggingEndpoint?.let { putString("overrideCustomLoggingEndpoint", it) }
        }
        config?.publishableKey?.let { putString("publishableKey", it) }
        config?.profileId?.let { putString("profileId", it) }
        config?.environment?.let { putString("environment", it.name) }
    }

    /**
     * Common function to build the base props bundle from HyperswitchBaseConfiguration.
     * Delegates to buildBundle() to avoid duplication.
     * Returns the inner props bundle directly (not wrapped in another Bundle).
     */
    private fun buildBasePropsBundle(
        config: HyperswitchBaseConfiguration?,
        configurationBundle: Bundle?,
        sdkAuthorization: String?,
        subscribedEvents: List<String>,
        customParams: Map<String, Any>? = null
    ): Bundle = requireNotNull(
        buildBundle(
            publishableKey = config?.publishableKey,
            sdkAuthorization = sdkAuthorization ?: "",
            configuration = configurationBundle,
            customBackendUrl = config?.customConfig?.overrideCustomBackendEndpoint,
            customLogUrl = config?.customConfig?.overrideCustomLoggingEndpoint,
            customParams = customParams,
            theme = configurationBundle?.getString("theme"),
            subscribedEvents = subscribedEvents,
            type = null,
            from = null,
            baseConfigurationBuilder = {
                putAll(getBaseConfigurationBundle(config))
            }
        ).getBundle("props")
    )

    /**
     * Creates a Bundle with all endpoint configuration passed through baseConfiguration.
     * For presentPaymentSheet flow.
     */
    fun getBundle(
        config: HyperswitchBaseConfiguration?,
        sdkAuthorization: String,
        configuration: PaymentSheet.Configuration? = null,
        subscribedEvents: List<String> = emptyList()
    ): Bundle = Bundle().apply {
        putBundle("props", buildBasePropsBundle(config, configuration?.bundle, sdkAuthorization, subscribedEvents).apply {
            putString("type", "payment")
        })
    }

    /**
     * Creates a Bundle for widget usage with all endpoint configuration passed through baseConfiguration.
     */
    fun getBundle(
        config: HyperswitchBaseConfiguration?,
        configuration: Bundle? = null,
        customParams: Map<String, Any>? = null,
        type: String? = "payment",
        from: String? = "nativeWidget",
        sdkAuthorization: String? = null,
        subscribedEvents: List<String> = emptyList(),
    ): Bundle = Bundle().apply {
        putBundle("props", buildBasePropsBundle(config, configuration, sdkAuthorization, subscribedEvents, customParams).apply {
            putString("type", type)
            putString("from", from)
        })
    }

    fun getJson(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ): JSONObject = toJson(getBundle(paymentIntentClientSecret, configuration))

    fun getJson(configurationMap: Map<*, *>): JSONObject =
        toJson(getMapWithHyperParams(configurationMap))

    private fun getMapWithHyperParams(map: Map<*, *>): Map<*, *> = mapOf(
        "props" to map.apply {
            plus(Pair("hyperParams", getHyperParamsMap(map)))
        }
    )

    // Get user agent
    private fun getUserAgent(context: Context?): String? =
        try {
            if (context == null)
                System.getProperty("http.agent")
            else
                WebSettings.getDefaultUserAgent(context)
        } catch (_: RuntimeException) {
            System.getProperty("http.agent")
        }


    @RequiresApi(Build.VERSION_CODES.R)
    private fun getRootWindowInsetsCompatR(rootView: View): EdgeInsets? {
        val insets =
            rootView.rootWindowInsets?.getInsets(
                WindowInsets.Type.statusBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.captionBar())
                ?: return null
        return EdgeInsets(
            top = insets.top.toFloat(),
            right = insets.right.toFloat(),
            bottom = insets.bottom.toFloat(),
            left = insets.left.toFloat())
    }

    private fun getRootWindowInsetsCompatBase(rootView: View): EdgeInsets? {
        val visibleRect = android.graphics.Rect()
        rootView.getWindowVisibleDisplayFrame(visibleRect)
        return EdgeInsets(
            top = visibleRect.top.toFloat(),
            right = (rootView.width - visibleRect.right).toFloat(),
            bottom = (rootView.height - visibleRect.bottom).toFloat(),
            left = visibleRect.left.toFloat())
    }

    private fun getBottomInset(context: Context?): EdgeInsets? {
        val activity = context as? Activity
        if(activity != null) {
            val rootView = context.window.decorView
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getRootWindowInsetsCompatR(
                    rootView
                )
                else -> getRootWindowInsetsCompatBase(rootView)
            }
        } else {
            return null
        }
    }

    // Get current time in milliseconds
    private fun getCurrentTime(): Double = System.currentTimeMillis().toDouble()

    fun fromBundle(bundle: Bundle): Map<*, *> {
        val map = mutableMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            val value = bundle[key]
            when {
                value == null -> {} //map[key] = null
                value.javaClass.isArray -> map[key] = value
                value is String -> map[key] = value
                value is Number -> map[key] = value as? Int ?: value.toDouble()
                value is Boolean -> map[key] = value
                value is Bundle -> map[key] = fromBundle(value)
                value is List<*> -> map[key] = value
                else -> throw IllegalArgumentException("Could not convert ${value.javaClass}")
            }
        }
        return map
    }

    fun toBundle(readableMap: Map<*, *>): Bundle {
        val bundle = Bundle()
        for ((key, value) in readableMap) {
            val keyString = key.toString()
            when (value) {
                null -> {} //bundle.putString(keyString, null)
                is Boolean -> bundle.putBoolean(keyString, value)
                is Number -> bundle.putDouble(keyString, value.toDouble())
                is String -> bundle.putString(keyString, value)
                is Map<*, *> -> bundle.putBundle(keyString, toBundle(value))
                is Array<*> -> bundle.putSerializable(keyString, value)
                else -> throw IllegalArgumentException("Could not convert object with key: $keyString.")
            }
        }
        return bundle
    }

    private fun toJson(bundle: Bundle): JSONObject {
        val json = JSONObject()
        for (key in bundle.keySet()) {
            when (val value = bundle[key]) {
                null -> {} //json.put(key, JSONObject.NULL)
                is Bundle -> json.put(key, toJson(value))
                is Boolean, is Int, is Float, is Long, is Double, is String -> json.put(key, value)
                is Array<*> -> json.put(key, JSONObject.wrap(value))
                is List<*> -> json.put(key, value)
                else -> throw IllegalArgumentException("Unsupported type for key: $key, $value")
            }
        }
        return json
    }

    private fun toJson(map: Map<*, *>): JSONObject {
        return JSONObject(map)
    }
}

data class EdgeInsets(val top: Float, val right: Float, val bottom: Float, val left: Float)