package io.hyperswitch.lite

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

object WebViewWarmUp {
    private val done = AtomicBoolean(false)

    fun isDone(): Boolean = done.get()

    fun init(context: Context) {
        if (done.get()) return

        Looper.getMainLooper().queue.addIdleHandler {
            var success = false
            try {
                val wv = WebView(context.applicationContext)
                Handler(Looper.getMainLooper()).post {
                    runCatching { wv.destroy() }
                }
                success = true
            } catch (e: Throwable) {
            } finally {
                if (success) done.set(true)
            }
            false
        }
    }
}
