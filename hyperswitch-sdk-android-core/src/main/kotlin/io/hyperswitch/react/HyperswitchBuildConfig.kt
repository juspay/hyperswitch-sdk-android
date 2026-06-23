package io.hyperswitch.react

import android.util.Log
import io.hyperswitch.core.BuildConfig as CoreBuildConfig

/**
 * Runtime bridge to read React-Native-specific build flags from the host
 * (:app) BuildConfig without coupling :core to a concrete RN version.
 *
 * Falls back to sensible defaults if the host BuildConfig is not available.
 */
object HyperswitchBuildConfig {

    private const val APP_BUILD_CONFIG_CLASS = "io.hyperswitch.BuildConfig"
    private const val TAG = "HyperswitchBuildConfig"

    val isNewArchitectureEnabled: Boolean
        get() = readAppFlag("IS_NEW_ARCHITECTURE_ENABLED", false)

    val isHermesEnabled: Boolean
        get() = readAppFlag("IS_HERMES_ENABLED", false)

    val isDebug: Boolean
        get() = readAppFlag("DEBUG", CoreBuildConfig.DEBUG)

    private inline fun <reified T> readAppFlag(name: String, default: T): T {
        return try {
            val clazz = Class.forName(APP_BUILD_CONFIG_CLASS)
            val field = clazz.getDeclaredField(name)
            field.get(null) as? T ?: default
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $name from $APP_BUILD_CONFIG_CLASS: ${e.message}")
            default
        }
    }
}
