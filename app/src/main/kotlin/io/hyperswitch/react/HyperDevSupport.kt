package io.hyperswitch.react

import android.content.Context
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.common.SurfaceDelegateFactory
import com.facebook.react.common.build.ReactBuildConfig
import com.facebook.react.devsupport.DevSupportManagerBase
import com.facebook.react.devsupport.DevSupportManagerFactory
import com.facebook.react.devsupport.ReactInstanceDevHelper
import com.facebook.react.devsupport.ReleaseDevSupportManager
import com.facebook.react.devsupport.interfaces.DevBundleDownloadListener
import com.facebook.react.devsupport.interfaces.DevLoadingViewManager
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.devsupport.interfaces.PausedInDebuggerOverlayManager
import com.facebook.react.devsupport.interfaces.RedBoxHandler
import com.facebook.react.packagerconnection.RequestHandler

/**
 * Development support for the SDK's React hosts (debug builds only).
 *
 * React Native's bridgeless dev support saves the bundle it downloads from the
 * dev server to one file per app (`BridgelessReactNativeDevBundle.js`). The SDK
 * runs several hosts in one process (payments, payment methods, payment method
 * management), each with its own entry bundle, so their downloads overwrite each
 * other and a host can end up evaluating another host's bundle ("Compiling JS
 * failed"). This is React Native's BridgelessDevSupportManager with a file (and
 * split-bundle directory) per entry; the class itself is internal to React Native.
 */
internal class HyperDevSupportManager(
    applicationContext: Context,
    reactInstanceManagerHelper: ReactInstanceDevHelper,
    packagerPathForJSBundleName: String?,
    enableOnCreate: Boolean,
    redBoxHandler: RedBoxHandler?,
    devBundleDownloadListener: DevBundleDownloadListener?,
    minNumShakes: Int,
    customPackagerCommandHandlers: Map<String, RequestHandler>?,
    surfaceDelegateFactory: SurfaceDelegateFactory?,
    devLoadingViewManager: DevLoadingViewManager?,
    pausedInDebuggerOverlayManager: PausedInDebuggerOverlayManager?,
) : DevSupportManagerBase(
    applicationContext,
    reactInstanceManagerHelper,
    packagerPathForJSBundleName,
    enableOnCreate,
    redBoxHandler,
    devBundleDownloadListener,
    minNumShakes,
    customPackagerCommandHandlers,
    surfaceDelegateFactory,
    devLoadingViewManager,
    pausedInDebuggerOverlayManager,
) {

    /**
     * Prefix of this host's dev bundle file and split-bundle directory, from its
     * entry (`index`, `index.payment-methods`, ...). The base class reads it while
     * it is being constructed, so it may only use state the base has set by then.
     */
    override val uniqueTag: String
        get() = "Bridgeless_" + (jsAppBundleName ?: "index").replace(Regex("[^A-Za-z0-9]"), "_")

    override fun handleReloadJS() {
        UiThreadUtil.assertOnUiThread()
        hideRedboxDialog()
        reactInstanceDevHelper.reload("HyperDevSupportManager.handleReloadJS()")
    }
}

/** Creates a [HyperDevSupportManager] for a host. */
internal class HyperDevSupportManagerFactory : DevSupportManagerFactory {

    @Deprecated("Old Architecture only; the SDK's hosts are bridgeless.")
    override fun create(
        applicationContext: Context,
        reactInstanceManagerHelper: ReactInstanceDevHelper,
        packagerPathForJSBundleName: String?,
        enableOnCreate: Boolean,
        redBoxHandler: RedBoxHandler?,
        devBundleDownloadListener: DevBundleDownloadListener?,
        minNumShakes: Int,
        customPackagerCommandHandlers: Map<String, RequestHandler>?,
        surfaceDelegateFactory: SurfaceDelegateFactory?,
        devLoadingViewManager: DevLoadingViewManager?,
        pausedInDebuggerOverlayManager: PausedInDebuggerOverlayManager?,
    ): DevSupportManager = ReleaseDevSupportManager()

    override fun create(
        applicationContext: Context,
        reactInstanceManagerHelper: ReactInstanceDevHelper,
        packagerPathForJSBundleName: String?,
        enableOnCreate: Boolean,
        redBoxHandler: RedBoxHandler?,
        devBundleDownloadListener: DevBundleDownloadListener?,
        minNumShakes: Int,
        customPackagerCommandHandlers: Map<String, RequestHandler>?,
        surfaceDelegateFactory: SurfaceDelegateFactory?,
        devLoadingViewManager: DevLoadingViewManager?,
        pausedInDebuggerOverlayManager: PausedInDebuggerOverlayManager?,
        useDevSupport: Boolean,
    ): DevSupportManager =
        if (useDevSupport) {
            HyperDevSupportManager(
                applicationContext,
                reactInstanceManagerHelper,
                packagerPathForJSBundleName,
                enableOnCreate,
                redBoxHandler,
                devBundleDownloadListener,
                minNumShakes,
                customPackagerCommandHandlers,
                surfaceDelegateFactory,
                devLoadingViewManager,
                pausedInDebuggerOverlayManager,
            )
        } else {
            ReleaseDevSupportManager()
        }

    companion object {
        /**
         * The factory for a host: this one in debug builds, React Native's default
         * otherwise (release, and React Native's own Fusebox perf builds).
         */
        @JvmStatic
        fun forBuild(debug: Boolean): DevSupportManagerFactory? =
            if (debug && !ReactBuildConfig.UNSTABLE_ENABLE_FUSEBOX_RELEASE) {
                HyperDevSupportManagerFactory()
            } else {
                null
            }
    }
}
