package io.hyperswitch.payments

import android.content.Context
import android.content.Intent

typealias Callback = (Map<String, Any?>) -> Unit

object GooglePayCallbackManager {
    private var callback: Callback? = null

    fun setCallback(appContext: Context, request: String, newCallback: Callback) {
        callback = newCallback
        val myIntent = Intent(
            appContext,
            GooglePayActivity::class.java
        )
        myIntent.putExtra("googlePayRequest", request)
        myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(myIntent)
    }

    fun getCallback(): Callback? {
        return callback
    }

    fun executeCallback(data: Map<String, Any?>) {
        callback?.invoke(data) ?: println("No callback set")
    }
}