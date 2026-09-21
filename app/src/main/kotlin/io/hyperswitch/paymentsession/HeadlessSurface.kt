package io.hyperswitch.paymentsession

import android.content.Context
import android.os.Bundle
import com.facebook.react.ReactHost
import com.facebook.react.interfaces.TaskInterface
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.runtime.ReactSurfaceView
import io.hyperswitch.react.SurfaceOwners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Viewless HyperHeadless surface on the shared host. It starts with a
 * ReactSurfaceView that never joins a window: prerender() would run React too,
 * but only a view-backed surface can be resolved by root tag, and that is how
 * the native modules find [owner] when JS replies.
 */
internal class HeadlessSurface private constructor(
    private val surface: ReactSurfaceImpl,
    private val view: ReactSurfaceView,
    private val startTask: TaskInterface<Void>,
) {

    /** Allocated when the view is created, so valid before the surface has started. */
    val rootTag: Int
        get() = view.rootViewTag

    /** Resolves once React is running this surface, which implies the host is up. */
    suspend fun awaitStarted() {
        withContext(Dispatchers.IO) { startTask.waitForCompletion() }
        startTask.getError()?.let { throw it }
    }

    /** Re-renders the running root with new props; React updates in place, no remount. */
    fun updateProps(props: Bundle) {
        surface.updateInitProps(props)
    }

    fun stop() {
        SurfaceOwners.attach(view, null)
        surface.stop()
        surface.detach()
    }

    companion object {
        private const val MODULE_NAME = "HyperHeadless"

        /**
         * Main thread. [owner] is what JS replies for this surface are routed to. [moduleName]
         * is the root the bundle registered; another host's bundle registers its own.
         */
        fun start(
            context: Context,
            reactHost: ReactHost,
            props: Bundle,
            owner: Any,
            moduleName: String = MODULE_NAME,
        ): HeadlessSurface {
            val surface = ReactSurfaceImpl(context, moduleName, props)
            val view = ReactSurfaceView(context, surface)
            SurfaceOwners.attach(view, owner)
            surface.attachView(view)
            surface.attach(reactHost)
            return HeadlessSurface(surface, view, surface.start())
        }
    }
}
