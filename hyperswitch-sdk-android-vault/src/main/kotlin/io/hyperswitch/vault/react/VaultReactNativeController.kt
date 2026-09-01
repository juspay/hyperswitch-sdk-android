package io.hyperswitch.vault.react

import android.app.Application
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.packagerconnection.PackagerConnectionSettings
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.DisplayMetricsHolder
import com.facebook.soloader.SoLoader
import io.hyperswitch.react.PackageList
import io.hyperswitch.vault.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * VaultReactNativeController
 *
 * Owns the single embedded React Native runtime shared by every vault field
 * surface of the Hyperswitch Vault SDK. The field UIs live in `assets://
 * hyperswitch-vault.bundle` (jsMainModule "index", app name "hs-vault") and are
 * mounted once per view (see io.hyperswitch.vault.widget.BaseVaultFieldView).
 */
object VaultReactNativeController {

    const val VAULT_MODULE_NAME = "hs-vault"

    private val reactHost = AtomicReference<ReactHost?>(null)

    private val isInitialized = AtomicBoolean(false)

    private val readyCallbacks = mutableListOf<(ReactContext) -> Unit>()

    fun getIsInitialized(): Boolean = isInitialized.get()

    fun getReactHost(): ReactHost = checkNotNull(reactHost.get()) {
        "React Native not initialized. Call VaultReactNativeController.initialize() first."
    }

    fun reactContextOrNull(): ReactContext? =
        reactHost.get()?.currentReactContext

    /** Event name for tokenise broadcasts; mirrors src/vault/registry.js. */
    const val TOKENISE_EVENT = "hsVaultTokenise"

    /**
     * Broadcasts a tokenise request to every JS vault surface running on this
     * runtime. The CVC surface listens and answers with the collected states
     * from the shared JS registry (see src/vault/registry.js).
     */
    fun emitTokenise() {
        reactContextOrNull()
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(TOKENISE_EVENT, Arguments.createMap())
    }

    /**
     * Broadcasts an answerable tokenise request: a mounted JS vault surface
     * claims the event, runs the vault confirm, and answers through
     * HyperVaultModule.returnTokenizedValue with the vaultSubmitResult JSON.
     */
    fun emitTokenise(sdkAuthorization: String, environment: String) {
        val payload = Arguments.createMap().apply {
            putString("sdkAuthorization", sdkAuthorization)
            putString("environment", environment)
        }
        reactContextOrNull()
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(TOKENISE_EVENT, payload)
    }

    fun initialize(application: Application) {
        synchronized(this) {
            if (isInitialized.get()) return

            SoLoader.init(application, OpenSourceMergedSoMapping)
            DisplayMetricsHolder.initDisplayMetricsIfNotInitialized(application.applicationContext)
            DefaultNewArchitectureEntryPoint.load()

            reactHost.set(
                DefaultReactHost.getDefaultReactHost(
                    context = application.applicationContext,
                    packageList = PackageList(application).packages.apply {
                        add(HyperVaultPackage())
                    },
                    jsMainModulePath = "index",
                    // Debug builds (SDK module) load the vault JS from Metro
                    // (`yarn start`) so fields can be iterated on live; release
                    // ships the prebuilt hermes bundle asset.
                    jsBundleFilePath =
                        if (BuildConfig.DEBUG) null else "assets://hyperswitch-vault.bundle",
                    useDevSupport = BuildConfig.DEBUG,
                )
            )

            isInitialized.set(true)
        }
    }

    /**
     * Runs [block] when the React context is available, starting the runtime if
     * needed. Vault field views call this before mounting their surface — the
     * view hierarchy can be created ahead of the JS runtime.
     */
    fun withReactContext(application: Application, block: (ReactContext) -> Unit) {
        reactContextOrNull()?.let {
            block(it)
            return
        }
        synchronized(readyCallbacks) {
            readyCallbacks.add(block)
        }
        reactHost.get()?.let { host ->
            host.addReactInstanceEventListener(object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) {
                    val pending = synchronized(readyCallbacks) {
                        readyCallbacks.toList().also { readyCallbacks.clear() }
                    }
                    pending.forEach { it(context) }
                    host.removeReactInstanceEventListener(this)
                }
            })
            host.start()
        }
    }
}
