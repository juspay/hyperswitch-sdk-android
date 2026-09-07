package io.hyperswitch.paymentsession

import android.content.Context
import android.os.Bundle
import com.facebook.react.ReactHost
import com.facebook.react.interfaces.fabric.ReactSurface
import com.facebook.react.runtime.ReactSurfaceImpl

/**
 * Viewless HyperHeadless surface. `prerender()` rather than `start()`: start() requires
 * an attached view; prerender runs React (effects included) without mounting.
 */
internal class HeadlessSurface(
    private val context: Context,
    private val reactHost: ReactHost,
) {

    private var surface: ReactSurface? = null

    fun start(props: Bundle) {
        if (surface != null) return
        val newSurface = ReactSurfaceImpl(context, MODULE_NAME, props)
        newSurface.attach(reactHost)
        surface = newSurface
        newSurface.prerender()
    }

    fun stop() {
        surface?.let { live ->
            live.stop()
            live.detach()
        }
        surface = null
    }

    private companion object {
        const val MODULE_NAME = "HyperHeadless"
    }
}
