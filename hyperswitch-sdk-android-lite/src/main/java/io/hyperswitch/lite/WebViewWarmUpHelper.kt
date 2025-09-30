package io.hyperswitch.lite

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

object WebViewWarmUpHelper {
    private val idleHandlerQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    fun warmUpWhenIdle(activity: Activity) {
        if (WebViewWarmUp.isDone()) {
            return
        }
        
        // Prevent multiple idle handlers from being queued
        if (idleHandlerQueued.compareAndSet(false, true)) {
            Looper.getMainLooper().queue.addIdleHandler {
                WebViewWarmUp.init(activity.applicationContext)
                false // Remove this IdleHandler after execution
            }
        }
    }

}
