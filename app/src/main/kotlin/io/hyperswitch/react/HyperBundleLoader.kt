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
 * `yarn bundle:android` builds every entry in one compilation and writes, next to
 * each other:
 *
 *  - one entry bundle per React host: `hyperswitch.bundle`,
 *    `hyperswitch-payment-methods.bundle`, `hyperswitch-payment-method-management.bundle`;
 *  - chunk files every entry shares, `hyperswitch.<chunk>.chunk.bundle`. The
 *    [INITIAL_CHUNKS] (React Native, and every other package the entries use from
 *    the start) are evaluated here, in the host's runtime, right before the entry:
 *    its startup waits for them. They only register module factories, so no native
 *    module needs to be ready. Every other chunk (sentry, vault, vgs, ...) is loaded
 *    on demand by the JS side through Re.Pack's `ScriptManager` module
 *    (`src/chunks/ScriptResolver.res`).
 *
 * [create] takes the path the host used before: `assets://<name>` for the packaged
 * copy, or an absolute file path for an OTA (Airborne) download. For a file, a
 * one-line script first tells the JS resolver which directory the entry came from
 * and which files it holds, so chunks shipped with that download are preferred and
 * any it lacks are read from the packaged assets.
 *
 * Nothing is started when a file the entry needs is missing ([missingFiles]): the
 * host's [HostHealth] records why, and the SDK reports it instead of crashing.
 */
object HyperBundleLoader {

    /** Shared chunks every entry needs before it starts, in load order; see rspack.config.mjs. */
    val INITIAL_CHUNKS = listOf(
        "hyperswitch.react-native.chunk.bundle",
        "hyperswitch.vendors.chunk.bundle",
    )

    private const val ASSET_SCHEME = "assets://"
    private const val BOOTSTRAP_FILE = "hyperswitch-bundle-layout.js"

    /**
     * The files [bundlePath] needs that are not in the app: the entry itself and the
     * [INITIAL_CHUNKS]. Empty when the host can start. An OTA download may leave out
     * initial chunks; the packaged copies then count.
     */
    @JvmStatic
    fun missingFiles(context: Context, bundlePath: String): List<String> {
        val assets = context.applicationContext.assets
        return if (bundlePath.startsWith(ASSET_SCHEME)) {
            val assetName = bundlePath.removePrefix(ASSET_SCHEME)
            val dir = assetName.substringBeforeLast('/', "")
            (listOf(assetName) + INITIAL_CHUNKS.map { if (dir.isEmpty()) it else "$dir/$it" })
                .filterNot { assetExists(assets, it) }
        } else {
            val bundleFile = File(bundlePath).absoluteFile
            val dir = bundleFile.parentFile
            val entry = if (bundleFile.isFile) emptyList() else listOf(bundleFile.path)
            entry + INITIAL_CHUNKS.filterNot { File(dir, it).isFile || assetExists(assets, it) }
        }
    }

    /**
     * A [JSBundleLoader] for [bundlePath] (`assets://<name>` or an absolute file path)
     * that evaluates the [INITIAL_CHUNKS] before the entry bundle. A missing file fails
     * [health] and the load, which React Native then reports to the host delegate.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        context: Context,
        bundlePath: String,
        loadSynchronously: Boolean = false,
        health: HostHealth? = null,
    ): JSBundleLoader {
        val appContext = context.applicationContext
        return object : JSBundleLoader() {
            override fun loadScript(delegate: JSBundleLoaderDelegate): String {
                val missing = missingFiles(appContext, bundlePath)
                if (missing.isNotEmpty()) {
                    val reason = "missing JavaScript: ${missing.joinToString()}"
                    throw health?.fail(reason) ?: IllegalStateException(reason)
                }
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
        // No layout script: with none, the JS resolver reads chunks from the assets,
        // which is where the packaged copies are.
        val dir = bundlePath.removePrefix(ASSET_SCHEME).substringBeforeLast('/', "")
        INITIAL_CHUNKS.forEach { chunk ->
            val asset = if (dir.isEmpty()) chunk else "$dir/$chunk"
            delegate.loadScriptFromAssets(assets, ASSET_SCHEME + asset, loadSynchronously)
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

        INITIAL_CHUNKS.forEach { chunk ->
            if (chunk in files) {
                val file = File(bundleDir, chunk)
                delegate.loadScriptFromFile(file.absolutePath, file.absolutePath, loadSynchronously)
            } else {
                // The download and the packaged copy must come from the same SDK build,
                // so OTA packages are expected to ship every chunk file.
                log("OTA bundle in $bundleDir has no $chunk; using the packaged copy")
                delegate.loadScriptFromAssets(context.assets, ASSET_SCHEME + chunk, loadSynchronously)
            }
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
