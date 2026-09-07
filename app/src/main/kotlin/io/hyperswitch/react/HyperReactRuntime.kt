package io.hyperswitch.react

import android.app.Application
import com.facebook.react.ReactHost
import io.hyperswitch.paymentsession.PaymentSessionRouter

/** One per PaymentSession: its own ReactHost (JS realm), emitter, router and updateIntent reply. */
class HyperReactRuntime(application: Application) {

    val eventEmitter = HyperEventEmitter()
    val sessionRouter = PaymentSessionRouter()

    @Volatile
    var onPrefetchUpdateIntentReply: ((type: String, resultJson: String) -> Unit)? = null

    val reactHost: ReactHost = ReactNativeController.createReactHost(application, this)

    fun destroy() {
        onPrefetchUpdateIntentReply = null
        eventEmitter.detach()
        reactHost.invalidate()
    }

    companion object {
        /** Negative so it never collides with an RN root tag. Must match iOS and PrefetchTask.res. */
        const val PREFETCH_SURFACE_TAG = -100
    }
}
