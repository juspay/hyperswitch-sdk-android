package io.hyperswitch.paymentsession

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import org.json.JSONObject

class LaunchOptions(
    private val context: Context? = null, private val sdkVersion: String
) {
    private fun buildHyperParamsMap(): Map<String, Any?> = buildMap {
        put("appId", context?.packageName)
        put("country", context?.resources?.configuration?.locales?.get(0)?.country)
        put("user-agent", getUserAgent(context))
        put("launchTime", getCurrentTime())
        put("sdkVersion", sdkVersion)
        put("device_model", Build.MODEL)
        put("os_type", "android")
        put("os_version", Build.VERSION.RELEASE)
        put("deviceBrand", Build.BRAND)
        getBottomInset(context)?.let {
            put("topInset", it.top)
            put("leftInset", it.left)
            put("rightInset", it.right)
            put("bottomInset", it.bottom)
        }
    }

    private fun getHyperParams(): Bundle = Bundle().apply {
        buildHyperParamsMap().forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Double -> putDouble(key, value)
                is Float -> putFloat(key, value)
            }
        }
    }

    private fun getHyperParamsMap(map: Map<*, *>): Map<String, Any?> {
        val base =
            (map["hyperParams"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
                ?: emptyMap()
        return base + buildHyperParamsMap()
    }

    fun getBundleWithHyperParams(
        readableMap: Map<*, *>, subscribedEvents: List<String> = emptyList()
    ): Bundle = Bundle().apply {
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
        customParams: Map<String, Any>?,
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
    private fun getBaseConfigurationBundle(config: HyperswitchBaseConfiguration?): Bundle =
        Bundle().apply {
            config?.publishableKey?.let { putString("publishableKey", it) }
            config?.profileId?.let { putString("profileId", it) }
            config?.environment?.let { putString("environment", it.name) }
            config?.customConfig?.let { customConfig ->
                when (customConfig) {
                    is CustomEndpointConfiguration.CustomEndpoint -> putString(
                        "customEndpoint", customConfig.url
                    )

                    is CustomEndpointConfiguration.OverrideEndpoints -> {
                        putBundle("overrideEndpoints", Bundle().apply {
                            customConfig.backendEndpoint?.let {
                                putString(
                                    "backendEndpoint", it
                                )
                            }
                            customConfig.assetsEndpoint?.let {
                                putString(
                                    "assetsEndpoint", it
                                )
                            }
                            customConfig.sdkConfigEndpoint?.let {
                                putString(
                                    "sdkConfigEndpoint", it
                                )
                            }
                            customConfig.confirmEndpoint?.let {
                                putString(
                                    "confirmEndpoint", it
                                )
                            }
                            customConfig.airborneEndpoint?.let {
                                putString(
                                    "airborneEndpoint", it
                                )
                            }
                            customConfig.loggingEndpoint?.let {
                                putString(
                                    "loggingEndpoint", it
                                )
                            }
                        })
                    }
                }
            }
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
            customParams = customParams,
            subscribedEvents = subscribedEvents,
            type = null,
            from = null,
            baseConfigurationBuilder = {
                putAll(getBaseConfigurationBundle(config))
            }).getBundle("props")
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
        putBundle(
            "props", buildBasePropsBundle(
                config, configuration?.bundle, sdkAuthorization, subscribedEvents
            ).apply {
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
        sdkAuthorization: String? = null,
        subscribedEvents: List<String> = emptyList(),
    ): Bundle = Bundle().apply {
        putBundle(
            "props", buildBasePropsBundle(
                config, configuration, sdkAuthorization, subscribedEvents, customParams
            ).apply {
                putString("type", type)
            })
    }

    fun getJson(
        config: HyperswitchConfiguration?,
        sdkAuthorization: String,
        configuration: PaymentSheet.Configuration?
    ): JSONObject = toJson(getBundle(config, sdkAuthorization, configuration))

    fun getJson(configurationMap: Map<*, *>): JSONObject =
        toJson(getMapWithHyperParams(configurationMap))

    private fun getMapWithHyperParams(map: Map<*, *>): Map<*, *> = mapOf(
        "props" to map.apply {
            plus(Pair("hyperParams", getHyperParamsMap(map)))
        })

    // Get user agent
    private fun getUserAgent(context: Context?): String? = try {
        if (context == null) System.getProperty("http.agent")
        else WebSettings.getDefaultUserAgent(context)
    } catch (_: RuntimeException) {
        System.getProperty("http.agent")
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private fun getRootWindowInsetsCompatR(rootView: View): EdgeInsets? {
        val insets = rootView.rootWindowInsets?.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.navigationBars() or WindowInsets.Type.captionBar()
        ) ?: return null
        return EdgeInsets(
            top = insets.top.toFloat(),
            right = insets.right.toFloat(),
            bottom = insets.bottom.toFloat(),
            left = insets.left.toFloat()
        )
    }

    private fun getRootWindowInsetsCompatBase(rootView: View): EdgeInsets? {
        val visibleRect = android.graphics.Rect()
        rootView.getWindowVisibleDisplayFrame(visibleRect)
        return EdgeInsets(
            top = visibleRect.top.toFloat(),
            right = (rootView.width - visibleRect.right).toFloat(),
            bottom = (rootView.height - visibleRect.bottom).toFloat(),
            left = visibleRect.left.toFloat()
        )
    }

    private fun getBottomInset(context: Context?): EdgeInsets? {
        val activity = context as? Activity
        if (activity != null) {
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