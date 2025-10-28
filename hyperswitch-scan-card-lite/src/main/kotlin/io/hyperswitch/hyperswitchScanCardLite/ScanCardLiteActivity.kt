package io.hyperswitch.hyperswitchScanCardLite

import io.hyperswitch.android.hscardscan.cardscan.CardScanSheetResult

class ScanCardLiteActivity : BaseScanCardActivity() {

    companion object {
        var callback: ScanCardCallback? = null
    }

    override fun processResult(result: CardScanSheetResult) {
        val resultMap = mutableMapOf<String, Any?>()

        when (result) {
            is CardScanSheetResult.Completed -> {
                resultMap["status"] = "Succeeded"
                val dataMap = mutableMapOf<String, String?>()
                dataMap["pan"] = result.scannedCard.pan
                resultMap["data"] = dataMap
            }
            is CardScanSheetResult.Canceled -> {
                resultMap["status"] = "Cancelled"
            }
            is CardScanSheetResult.Failed -> {
                resultMap["status"] = "Failed"
            }
        }

        callback?.onScanResult(resultMap)
    }
}
