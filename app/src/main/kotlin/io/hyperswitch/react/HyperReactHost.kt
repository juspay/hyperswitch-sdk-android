package io.hyperswitch.react

import android.app.Application
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.ReactContext
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.defaults.DefaultComponentsRegistry
import com.facebook.react.defaults.DefaultReactHostDelegate
import com.facebook.react.defaults.DefaultTurboModuleManagerDelegate
import com.facebook.react.fabric.ComponentFactory
import com.facebook.react.runtime.ReactHostImpl
import com.facebook.react.runtime.hermes.HermesInstance
import io.hyperswitch.BuildConfig

/**
 * One of the SDK's React hosts (payments, payment methods, payment method management):
 * the construction of DefaultReactHost.getDefaultReactHost, over the host's own entry
 * bundle, packages and [health].
 *
 * Unlike React Native's default, nothing the host throws while starting ends the app:
 * a missing bundle or one that cannot be loaded fails [health], and the SDK reports
 * that from every call that needs the host.
 */
@OptIn(UnstableReactNativeAPI::class)
internal fun createHyperReactHost(
    application: Application,
    entryFile: String,
    bundlePath: String,
    packages: List<ReactPackage>,
    health: HostHealth,
): ReactHost {
    // A release build only ever loads what the app packages, so a missing file is known
    // now. A debug build may be served by the dev server instead; the loader decides.
    if (!BuildConfig.DEBUG) {
        val missing = HyperBundleLoader.missingFiles(application, bundlePath)
        if (missing.isNotEmpty()) health.fail("missing JavaScript: ${missing.joinToString()}")
    }

    val delegate = DefaultReactHostDelegate(
        jsMainModulePath = entryFile,
        jsBundleLoader = HyperBundleLoader.create(application, bundlePath, loadSynchronously = true, health = health),
        reactPackages = packages,
        jsRuntimeFactory = HermesInstance(),
        turboModuleManagerDelegateBuilder = DefaultTurboModuleManagerDelegate.Builder(),
        exceptionHandler = health::handleInstanceException,
    )

    val componentFactory = ComponentFactory()
    DefaultComponentsRegistry.register(componentFactory)

    return ReactHostImpl(
        context = application,
        reactHostDelegate = delegate,
        componentFactory = componentFactory,
        allowPackagerServerAccess = true,
        useDevSupport = BuildConfig.DEBUG,
        // A dev bundle file of its own: the hosts share one process.
        devSupportManagerFactory = HyperDevSupportManagerFactory.forBuild(BuildConfig.DEBUG),
    ).also { host ->
        host.addReactInstanceEventListener(object : ReactInstanceEventListener {
            override fun onReactContextInitialized(context: ReactContext) = health.markStarted()
        })
    }
}
