package io.hyperswitch.react

import android.content.Context
import android.content.res.AssetManager
import com.facebook.react.bridge.JSBundleLoader
import com.facebook.react.bridge.JSBundleLoaderDelegate
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Loads the SDK's split JavaScript the way the bundler (Re.Pack) lays it out.
 *
 * `yarn bundle:android` writes an entry bundle (`hyperswitch.bundle`,
 * `hyperswitch-payment-methods.bundle`) and, next to it, chunk files named
 * `<entry name>.<chunk>.chunk.bundle`:
 *
 *  - `<entry name>.react-native.chunk.bundle` holds React Native and React. The
 *    entry's startup waits for it, so it is evaluated here, in the same runtime,
 *    right before the entry. It only registers module factories, so it needs no
 *    native module to be ready.
 *  - every other chunk (sentry, paypal, netcetera-3ds, scancard, vault, vgs, ...)
 *    is loaded on demand by the JS side through Re.Pack's `ScriptManager` native
 *    module (`src/chunks/ScriptResolver.res`).
 *
 * [create] takes the path the host used before: `assets://<name>` for the
 * packaged copy, or an absolute file path for an OTA (Airborne) download. For a
 * file, a one-line script first tells the JS resolver which directory the entry
 * came from and which files it holds, so chunks shipped with that download are
 * preferred and any it lacks are read from the packaged assets.
 *
 * A bundle built without splitting has no runtime chunk and loads as before.
 */
object HyperBundleLoader {

    private const val ASSET_SCHEME = "assets://"
    private const val BUNDLE_EXTENSION = ".bundle"
    private const val RUNTIME_CHUNK = "react-native"
    private const val BOOTSTRAP_FILE = "hyperswitch-bundle-layout.js"

    /** File name of the runtime chunk that belongs to [bundleFileName]. */
    @JvmStatic
    fun runtimeChunkName(bundleFileName: String): String =
        "${bundleFileName.removeSuffix(BUNDLE_EXTENSION)}.$RUNTIME_CHUNK.chunk$BUNDLE_EXTENSION"

    /**
     * A [JSBundleLoader] for [bundlePath] (`assets://<name>` or an absolute file
     * path) that evaluates the runtime chunk, when there is one, before the entry.
     */
    @JvmStatic
    @JvmOverloads
    fun create(context: Context, bundlePath: String, loadSynchronously: Boolean = false): JSBundleLoader {
        val appContext = context.applicationContext
        return object : JSBundleLoader() {
            override fun loadScript(delegate: JSBundleLoaderDelegate): String {
                if (bundlePath.startsWith(ASSET_SCHEME)) {
                    loadFromAssets(appContext.assets, delegate, bundlePath, loadSynchronously)
                } else {
                    loadFromFile(appContext, delegate, bundlePath, loadSynchronously)
                }
                return bundlePath
            }
        }
    }

    private fun loadFromAssets(
        assets: AssetManager,
        delegate: JSBundleLoaderDelegate,
        bundlePath: String,
        loadSynchronously: Boolean,
    ) {
        val assetName = bundlePath.removePrefix(ASSET_SCHEME)
        val dir = assetName.substringBeforeLast('/', "")
        val runtimeName = runtimeChunkName(assetName.substringAfterLast('/'))
        val runtimeAsset = if (dir.isEmpty()) runtimeName else "$dir/$runtimeName"
        // No layout script: with none, the JS resolver reads chunks from the assets.
        if (assetExists(assets, runtimeAsset)) {
            delegate.loadScriptFromAssets(assets, ASSET_SCHEME + runtimeAsset, loadSynchronously)
        }
        delegate.loadScriptFromAssets(assets, bundlePath, loadSynchronously)
    }

    private fun loadFromFile(
        context: Context,
        delegate: JSBundleLoaderDelegate,
        bundlePath: String,
        loadSynchronously: Boolean,
    ) {
        val bundleFile = File(bundlePath).absoluteFile
        val bundleDir = bundleFile.parentFile
        val files = bundleDir?.list()?.toList().orEmpty()

        writeLayout(context, bundleDir, files)?.let { layout ->
            delegate.loadScriptFromFile(layout.absolutePath, layout.absolutePath, loadSynchronously)
        }

        val runtimeName = runtimeChunkName(bundleFile.name)
        if (runtimeName in files) {
            val runtimeFile = File(bundleDir, runtimeName)
            delegate.loadScriptFromFile(runtimeFile.absolutePath, runtimeFile.absolutePath, loadSynchronously)
        } else if (assetExists(context.assets, runtimeName)) {
            // The download and the packaged copy must come from the same SDK build,
            // so OTA packages are expected to ship every chunk file.
            log("OTA bundle in $bundleDir has no $runtimeName; using the packaged copy")
            delegate.loadScriptFromAssets(context.assets, ASSET_SCHEME + runtimeName, loadSynchronously)
        }

        delegate.loadScriptFromFile(bundleFile.absolutePath, bundleFile.absolutePath, loadSynchronously)
    }

    /**
     * Writes the script that describes the bundle layout to the JS chunk resolver
     * (`globalThis.__HYPERSWITCH_SCRIPTS__`). Null if it cannot be written.
     */
    private fun writeLayout(context: Context, bundleDir: File?, files: List<String>): File? {
        if (bundleDir == null) return null
        return try {
            val layout = JSONObject()
                .put("bundleDir", bundleDir.absolutePath)
                .put("bundleFiles", JSONArray(files))
                .put("resourceDir", JSONObject.NULL)
            val file = File(context.cacheDir, BOOTSTRAP_FILE)
            file.writeText("globalThis.__HYPERSWITCH_SCRIPTS__=$layout;")
            file
        } catch (e: Exception) {
            log("Could not write the bundle layout: ${e.message}")
            null
        }
    }

    private fun assetExists(assets: AssetManager, name: String): Boolean =
        try {
            assets.open(name).close()
            true
        } catch (_: IOException) {
            false
        }

    private fun log(message: String) {
        try {
            HyperLogManager.addLog(
                HSLog.LogBuilder()
                    .value(message)
                    .category(LogCategory.API)
                    .logType("warning")
                    .build()
            )
        } catch (_: Exception) {}
    }
}
