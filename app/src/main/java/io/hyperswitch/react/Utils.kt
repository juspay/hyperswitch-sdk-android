package io.hyperswitch.react

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.ReactFragment
import io.hyperswitch.BuildConfig
import java.util.Locale

class Utils {
  companion object {
    @JvmStatic lateinit var reactNativeFragmentCard: ReactFragment
    @JvmStatic var reactNativeFragmentSheet: ReactFragment? = null
    @JvmStatic var lastRequest: Bundle? = null
    @JvmStatic var flags: Int = 0
    @JvmStatic var oldContext: AppCompatActivity? = null

    // Open React view method
    fun openReactView(
      context: AppCompatActivity,
      request: Map<String, Any?>,
      message: String,
      id: Int?
    ) {
      // Run on UI thread
      context.runOnUiThread {
        // Begin fragment transaction
        val transaction = context.supportFragmentManager.beginTransaction()
        val requestMap = convertMapToBundle(request)

        // Lock screen orientation
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        // Check message type and set window flags accordingly
        if (arrayOf("card", "google_pay", "paypal", "expressCheckout").indexOf(message) < 0) {
          flags = context.window.attributes.flags
          if (message != "unifiedCheckout") {
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
          } else {
            context.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            context.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
          }

          // Check if React Native fragment exists or if request has changed
          if (reactNativeFragmentSheet == null || areBundlesNotEqual(requestMap, lastRequest, context)) {
            lastRequest = requestMap
            reactNativeFragmentSheet = HyperswitchFragment.Builder()
              .setComponentName("hyperSwitch")
              .setLaunchOptions(getLaunchOptions(requestMap, message, context))
              .setFabricEnabled(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)
              .build()
            transaction.replace(android.R.id.content, reactNativeFragmentSheet!!).commit()
          } else {
            transaction.show(reactNativeFragmentSheet!!).commit()
          }
        } else {
          flags = 0
          reactNativeFragmentCard = HyperswitchFragment.Builder()
            .setComponentName("hyperSwitch")
            .setLaunchOptions(getLaunchOptions(requestMap, message, context))
            .setFabricEnabled(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)
            .build()
          transaction.add(id ?: android.R.id.content, reactNativeFragmentCard).commit()
        }

        // Unregister saved state provider
        context.supportFragmentManager
          .addFragmentOnAttachListener { _, _ ->
            context.savedStateRegistry.unregisterSavedStateProvider("android:support:fragments")
          }
      }
    }

    // Check if bundles are not equal
    private fun areBundlesNotEqual(bundle1: Bundle?, bundle2: Bundle?, context: AppCompatActivity): Boolean {
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
    private fun getLaunchOptions(request: Bundle, message: String, context: AppCompatActivity): Bundle {
      request.putString("type", message)

      val hyperParams = request.getBundle("hyperParams") ?: Bundle()
      hyperParams.putString("appId", context.packageName)
      hyperParams.putString("country", context.resources.configuration.locale.country)
      hyperParams.putString("user-agent", getUserAgent(context))
      hyperParams.putString("ip", getDeviceIPAddress(context))
      hyperParams.putDouble("launchTime", getCurrentTime())

      request.putBundle("hyperParams", hyperParams)

      val bundle = Bundle()
      bundle.putBundle("props", request)
      return bundle
    }

    // Hide React fragment
    fun hideFragment(context: AppCompatActivity, reset: Boolean) {
      if (reactNativeFragmentSheet != null) {
        context.supportFragmentManager
          .beginTransaction()
          .hide(reactNativeFragmentSheet!!)
          .commit()
      }
      context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      if (flags != 0) {
        context.runOnUiThread {
          context.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
          context.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
          context.window.addFlags(flags)
        }
      }
      if (reset) {
        reactNativeFragmentSheet = null
      }
    }

    // Handle back press for React fragment
    fun onBackPressed(): Boolean {
      return if (reactNativeFragmentSheet == null || reactNativeFragmentSheet!!.isHidden) {
        false
      } else {
        reactNativeFragmentSheet!!.onBackPressed()
        true
      }
    }

    // Convert Map to Bundle
    private fun convertMapToBundle(input: Map<String, Any?>): Bundle {
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
  }
}
