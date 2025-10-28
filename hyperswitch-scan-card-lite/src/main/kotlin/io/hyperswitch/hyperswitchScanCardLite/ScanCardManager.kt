package io.hyperswitch.hyperswitchScanCardLite

import android.app.Activity
import android.content.Intent

object ScanCardManager {
    private var scanCallback: ScanCardCallback? = null

    @JvmStatic
    fun launch(activity: Activity, callback: ScanCardCallback) {
        ScanCardLiteActivity.callback = callback
        val intent = Intent(activity, ScanCardLiteActivity::class.java)
        activity.startActivity(intent)
    }

    internal fun getCallback(): ScanCardCallback? = scanCallback
}
