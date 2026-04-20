package io.hyperswitch.paymentsession

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import io.hyperswitch.PaymentConfiguration
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
        sessionToken: String,
        configuration: PaymentSheet.Configuration? = null
    ): Bundle =
        context?.let { getBundle(it, sessionToken, configuration) } ?: Bundle()

    fun getBundle(
        context: Context,
        sessionToken: String,
        configuration: PaymentSheet.Configuration? = null
    ): Bundle = Bundle().apply {
        putBundle("props", Bundle().apply {
            putString("type", "payment")
            putString(
                "publishableKey",
                PaymentConfiguration.getInstance(context).publishableKey
            )
            putString("sdkAuthorization", sessionToken)
            putString(
                "customBackendUrl",
                PaymentConfiguration.getInstance(context).customBackendUrl
            )
            putString("customLogUrl", PaymentConfiguration.getInstance(context).customLogUrl)
            putString("theme", configuration?.appearance?.theme?.name)
            putBundle("customParams", PaymentConfiguration.getInstance(context).customParams)
            putBundle("configuration", configuration?.bundle)
            putBundle("hyperParams", getHyperParams())
        })
    }

    fun getBundle(
        publishableKey: String? = null,
        configuration: Map<String, Any>? = null,
        customBackendUrl: String? = null,
        customLogUrl: String? = null,
        customParams: Map<String, Any>? = null,
        type: String? = "payment",
        widgetId: String? = null,
        sdkAuthorization : String? = null,
    ): Bundle = Bundle().apply {
        putBundle("props", Bundle().apply {
            putString("type", type)
            putString("from", "rn")
            putString("publishableKey", publishableKey ?: "")
            putString("sdkAuthorization", sdkAuthorization?:"")
            putBundle("configuration", toBundle(configuration ?: emptyMap<String, Any>()))
            customBackendUrl?.let { url -> putString("customBackendUrl", url) }
            customLogUrl?.let { url -> putString("customLogUrl", url) }

            if (configuration?.containsKey("subscribedEvents") == true) {
                val subscribedEventsArray = configuration["subscribedEvents"] as? List<*>
                if (subscribedEventsArray != null) {
                    putSerializable("subscribedEvents", ArrayList(subscribedEventsArray))
                }
            }
            customParams?.let { params ->
                putBundle(
                    "customParams", toBundle(params)
                )
            }
            putBundle("hyperParams", getHyperParams())
            putString("widgetId", widgetId)
        })
    }

    fun getBundleWithHyperParams(readableMap: Map<*, *>): Bundle = Bundle().apply {
        putBundle("props", toBundle(readableMap).apply {
            putBundle("hyperParams", getHyperParams())
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