package io.hyperswitch.hyperswitchScanCardLite

interface ScanCardCallback {
    fun onScanResult(result: Map<String, Any?>)
}