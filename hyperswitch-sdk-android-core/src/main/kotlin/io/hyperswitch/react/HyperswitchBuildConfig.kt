package io.hyperswitch.react

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import io.hyperswitch.core.BuildConfig as CoreBuildConfig

/**
 * Runtime bridge to read React-Native-specific build flags from the host app.
 *
 * First tries to read `<meta-data>` values merged into the host manifest
 * (this is how SDK libraries can publish flags without the merchant touching
 * their app's build.gradle). Falls back to the legacy `io.hyperswitch.BuildConfig`
 * lookup for the client-core demo app, then to safe defaults.
 */
object HyperswitchBuildConfig {

    private const val LEGACY_APP_BUILD_CONFIG_CLASS = "io.hyperswitch.BuildConfig"
    private const val TAG = "HyperswitchBuildConfig"

    private var application: Application? = null

    fun setApplication(application: Application) {
        this.application = application
    }

    val isNewArchitectureEnabled: Boolean
        get() = readFlag("IS_NEW_ARCHITECTURE_ENABLED", "io.hyperswitch.new_architecture_enabled", false)

    val isHermesEnabled: Boolean
        get() = readFlag("IS_HERMES_ENABLED", "io.hyperswitch.hermes_enabled", true)

    val loadSoFiles: Boolean
        get() = readFlag("LOAD_SO_FILES", "io.hyperswitch.load_so_files", true)

    val isDebug: Boolean
        get() = readAppFlag("DEBUG", CoreBuildConfig.DEBUG)

    private fun readFlag(buildConfigName: String, metaDataName: String, default: Boolean): Boolean {
        readMetaDataFlag(metaDataName, default)?.let { return it }
        readAppFlag(buildConfigName, default).let { return it }
    }

    private fun readMetaDataFlag(name: String, default: Boolean): Boolean? {
        val app = application ?: return default
        return try {
            val appInfo = app.packageManager.getApplicationInfo(
                app.packageName,
                PackageManager.GET_META_DATA
            )
            if(appInfo.metaData.containsKey(name)) {
                val data = when (val value = appInfo.metaData?.getString(name)) {
                    is String -> value.toBooleanStrictOrNull()
                    else -> appInfo.metaData.getBoolean(name)
                }
                data ?: default
            }else{
                default
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $name from manifest: ${e.message}")
            default
        }
    }

    private inline fun <reified T> readAppFlag(name: String, default: T): T {
        return try {
            val clazz = Class.forName(LEGACY_APP_BUILD_CONFIG_CLASS)
            val field = clazz.getDeclaredField(name)
            field.get(null) as? T ?: default
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $name from $LEGACY_APP_BUILD_CONFIG_CLASS: ${e.message}")
            default
        }
    }
}
