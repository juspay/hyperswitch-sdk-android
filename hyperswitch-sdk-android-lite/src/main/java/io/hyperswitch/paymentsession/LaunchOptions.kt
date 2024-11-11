package io.hyperswitch.paymentsession

import android.app.Activity
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.lite.BuildConfig
import io.hyperswitch.paymentsheet.PaymentSheet
import org.json.JSONObject
import java.util.Locale

class LaunchOptions(private val activity: Activity? = null) {

    private fun getHyperParams(configuration: PaymentSheet.Configuration? = null): Bundle =
        Bundle().apply {
            putString("appId", activity?.packageName)
            putString("country", activity?.resources?.configuration?.locale?.country)
            putString("user-agent", getUserAgent(activity))
            putString("ip", getDeviceIPAddress(activity))
            putDouble("launchTime", getCurrentTime())
            putString("sdkVersion", BuildConfig.VERSION_NAME)
            configuration?.disableBranding?.let {
                putBoolean(
                    "disableBranding", it
                )
            }
            configuration?.defaultView?.let {
                putBoolean(
                    "defaultView", it
                )
            }
        }

    private fun getHyperParamsMap(map: Map<*, *>): Map<*, *> =
        (map["hyperParams"] as? Map<*, *> ?: mutableMapOf<String, Any?>()).apply {
            plus(Pair("appId", activity?.packageName))
            plus(Pair("country", activity?.resources?.configuration?.locale?.country))
            plus(Pair("user-agent", getUserAgent(activity)))
            plus(Pair("ip", getDeviceIPAddress(activity)))
            plus(Pair("launchTime", getCurrentTime()))
            plus(Pair("sdkVersion", BuildConfig.VERSION_NAME))
        }

    fun getBundle(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null
    ): Bundle =
        activity?.let { getBundle(it, paymentIntentClientSecret, configuration) } ?: Bundle()

    fun getBundle(
        context: Context,
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null
    ): Bundle = Bundle().apply {
        putBundle("props", Bundle().apply {
            putString("type", "payment")
            putString(
                "publishableKey",
                PaymentConfiguration.getInstance(context).publishableKey
            )
            putString("clientSecret", paymentIntentClientSecret)
            putString(
                "customBackendUrl",
                PaymentConfiguration.getInstance(context).customBackendUrl
            )
            putString("customLogUrl", PaymentConfiguration.getInstance(context).customLogUrl)
            putString("theme", configuration?.appearance?.theme?.name)
            putBundle("customParams", PaymentConfiguration.getInstance(context).customParams)
            putBundle("configuration", configuration?.bundle)
            putBundle("hyperParams", getHyperParams(configuration))
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
            if (context == null){
                Log.w("LaunchOptions","Context is null, falling back to system http.agent")
                System.getProperty("http.agent") ?: null
            }
            else
                WebSettings.getDefaultUserAgent(context)
        } catch (e: RuntimeException) {
            Log.w("LaunchOptions","Failed to get WebView user agent, falling back to system http.agent", e)
            System.getProperty("http.agent") ?: null
        }


    // Get device IP address
    private fun getDeviceIPAddress(context: Context?): String? {
        if (context == null) return null
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        return String.format(
            Locale.getDefault(), "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
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
                value is Number -> map[key] = if (value is Int) value else value.toDouble()
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
                is Array<*> -> bundle.putSerializable(keyString, value as? Array<*>)
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