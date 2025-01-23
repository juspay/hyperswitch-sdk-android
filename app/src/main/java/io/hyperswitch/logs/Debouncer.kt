package io.hyperswitch.logs

import android.os.Handler
import android.os.Looper

class Debouncer(private val delayInMillis: Long) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    fun debounce(action: () -> Unit) {
        runnable?.let { handler.removeCallbacks(it) } // Remove any previously queued actions
        runnable = Runnable { action() }
        handler.postDelayed(runnable!!, delayInMillis) // Schedule the new action
    }

    fun cancel() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}