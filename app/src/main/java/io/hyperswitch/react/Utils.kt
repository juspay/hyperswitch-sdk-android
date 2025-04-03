package io.hyperswitch.react
import android.os.Build
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Parcelable
import android.webkit.WebSettings
import androidx.fragment.app.FragmentActivity
import com.facebook.react.ReactFragment
import io.hyperswitch.BuildConfig
import java.util.Locale
enum class SDKEnvironment{SANDBOX,PROD}

class Utils {
  companion object {
    @JvmStatic var lastRequest: Bundle? = null
    @JvmStatic var oldContext: FragmentActivity? = null

    // Open React view method
    fun openReactView(
      context: FragmentActivity,
      request: Bundle,
      message: String,
      id: Int?
    ) {
      // Run on UI thread
      context.runOnUiThread {
        // Begin fragment transaction
        val transaction = context.supportFragmentManager.beginTransaction()

        // Check message type and set window flags accordingly
        if (arrayOf("card", "google_pay", "paypal", "expressCheckout").indexOf(message) < 0) {

          val reactNativeFragmentSheet = context.supportFragmentManager.findFragmentByTag("paymentSheet")

          // Check if React Native fragment exists or if request has changed
          if (reactNativeFragmentSheet == null || areBundlesNotEqual(request, lastRequest, context)) {
            lastRequest = request
            val newReactNativeFragmentSheet = ReactFragment.Builder()
              .setComponentName("hyperSwitch")
              .setLaunchOptions(getLaunchOptions(request, message, context))
              .setFabricEnabled(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)
              .build()
            transaction.replace(android.R.id.content, newReactNativeFragmentSheet, "paymentSheet").commitAllowingStateLoss()
          } else {
            transaction.show(reactNativeFragmentSheet).commitAllowingStateLoss()
          }
        } else {
          val reactNativeFragmentCard = ReactFragment.Builder()
            .setComponentName("hyperSwitch")
            .setLaunchOptions(getLaunchOptions(request, message, context))
            .setFabricEnabled(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)
            .build()
          transaction.add(id ?: android.R.id.content, reactNativeFragmentCard, "cardForm").commitAllowingStateLoss()
        }
      }
    }

    // Check if bundles are not equal
    private fun areBundlesNotEqual(bundle1: Bundle?, bundle2: Bundle?, context: FragmentActivity): Boolean {
      if (bundle1 == null || bundle2 == null || (oldContext !== null && oldContext !== context)) {
        return true
      }
      oldContext = context
      return !(bundle1.getString("publishableKey") == bundle2.getString("publishableKey")
              && bundle1.getString("clientSecret") == bundle2.getString("clientSecret")
              && bundle1.getString("type") == bundle2.getString("type"))
    }

    // Get user agent
    fun getUserAgent(context: Context): String {
      return try {
        WebSettings.getDefaultUserAgent(context)
      } catch (e: RuntimeException) {
        System.getProperty("http.agent") ?: ""
      }
    }

    // Get device IP address
    fun getDeviceIPAddress(context: Context): String {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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

    // Get launch options for React Native fragment
    private fun getLaunchOptions(request: Bundle, message: String, context: FragmentActivity): Bundle {
      request.putString("type", message)
      val hyperParams = request.getBundle("hyperParams") ?: Bundle()
      hyperParams.putString("appId", context.packageName)
      hyperParams.putString("country", context.resources.configuration.locale.country)
      hyperParams.putString("user-agent", getUserAgent(context))
//      hyperParams.putString("ip", getDeviceIPAddress(context))
      hyperParams.putDouble("launchTime", getCurrentTime())
      hyperParams.putString("sdkVersion", BuildConfig.VERSION_NAME)
      hyperParams.putString("device_model", Build.MODEL)
      hyperParams.putString("os_type", "android")
      hyperParams.putString("os_version", Build.VERSION.RELEASE)
      hyperParams.putString("deviceBrand", Build.BRAND)
      request.putBundle("hyperParams", hyperParams)

      val bundle = Bundle()
      bundle.putBundle("props", request)
      return bundle
    }

    // Hide React fragment
    fun hideFragment(context: FragmentActivity, reset: Boolean) {
      val reactNativeFragmentSheet = context.supportFragmentManager.findFragmentByTag("paymentSheet")
      if (reactNativeFragmentSheet != null) {
        try {
          context.supportFragmentManager
            .beginTransaction()
            .hide(reactNativeFragmentSheet)
            .commitAllowingStateLoss()
        } catch(_: Exception) {}
      }
    }

    // Handle back press for React fragment
    fun onBackPressed(context: FragmentActivity): Boolean {
      val reactNativeFragmentSheet = context.supportFragmentManager.findFragmentByTag("paymentSheet") as? ReactFragment
      return if (reactNativeFragmentSheet == null || reactNativeFragmentSheet.isHidden) {
        false
      } else {
        reactNativeFragmentSheet.onBackPressed()
        true
      }
    }

    // Convert Map to Bundle
    fun convertMapToBundle(input: Map<String, Any?>): Bundle {
      val bundle = Bundle()

      for ((key, value) in input) {
        when (value) {
          is String -> bundle.putString(key, value)
          is Boolean -> bundle.putBoolean(key, value)
          is Int -> bundle.putInt(key, value)
          is Double -> bundle.putDouble(key, value)
          is Float -> bundle.putFloat(key, value)
          is Long -> bundle.putLong(key, value)
          is Char -> bundle.putChar(key, value)
          is CharSequence -> bundle.putCharSequence(key, value)
          is Parcelable -> bundle.putParcelable(key, value)
          is IntArray -> bundle.putIntArray(key, value)
          is BooleanArray -> bundle.putBooleanArray(key, value)
          is ByteArray -> bundle.putByteArray(key, value)
          is ShortArray -> bundle.putShortArray(key, value)
          is DoubleArray -> bundle.putDoubleArray(key, value)
          is FloatArray -> bundle.putFloatArray(key, value)
          is LongArray -> bundle.putLongArray(key, value)
          is CharArray -> bundle.putCharArray(key, value)
          is Map<*, *> -> bundle.putBundle(key, @Suppress("UNCHECKED_CAST") convertMapToBundle(value as Map<String, Any?>))
        }
      }

      return bundle
    }

    // Get current time in milliseconds
    fun getCurrentTime(): Double {
      return System.currentTimeMillis().toDouble()
    }
    fun checkEnvironment(publishableKey: String): SDKEnvironment {
      return if (publishableKey.isNotEmpty() && publishableKey.startsWith("pk_prd_")) {
        SDKEnvironment.PROD
      } else {
        SDKEnvironment.SANDBOX
      }
    }

    fun getLoggingUrl(publishableKey: String): String{
      return if (checkEnvironment(publishableKey) == SDKEnvironment.PROD)
        "https://api.hyperswitch.io/logs/sdk"
      else
        "https://sandbox.hyperswitch.io/logs/sdk"
    }
  }
}
