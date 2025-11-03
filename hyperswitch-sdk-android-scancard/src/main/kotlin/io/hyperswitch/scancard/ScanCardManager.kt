package io.hyperswitch.scancard

import androidx.fragment.app.FragmentActivity

object ScanCardManager {
    private var scanCallback: ScanCardCallback? = null

    @JvmStatic
    fun launch(activity: FragmentActivity, callback: ScanCardCallback) {
        scanCallback = callback
        val fragment = ScanCardFragment()
        activity.runOnUiThread {
            activity.supportFragmentManager.beginTransaction()
                .add(fragment, "ScanCardFragment")
                .commitNow()
        }
    }

    internal fun getCallback(): ScanCardCallback? = scanCallback
}

interface ScanCardCallback {
    fun onScanResult(result: Map<String, Any?>)
}