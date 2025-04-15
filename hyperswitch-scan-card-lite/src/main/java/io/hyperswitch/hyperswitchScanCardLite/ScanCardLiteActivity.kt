package io.hyperswitch.hyperswitchScanCardLite


import io.hyperswitch.android.hscardscan.cardscan.CardScanSheetResult
import io.hyperswitch.hyperswitchScanCardLite.BaseScanCardActivity
import io.hyperswitch.hyperswitchScanCardLite.ScanCardCallback

//
//import android.os.Bundle
//import androidx.appcompat.app.AppCompatActivity
//import io.hyperswitch.android.hscardscan.cardscan.CardScanSheet
//import io.hyperswitch.android.hscardscan.cardscan.CardScanSheetResult
//
//class ScanCardLiteActivity : AppCompatActivity() {
//
//    companion object {
//        var callback: ScanCardCallback? = null
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        val cardScanSheet = CardScanSheet.create(this, ::onScanFinished)
//        cardScanSheet.present()
//    }
//
//    private fun onScanFinished(result: CardScanSheetResult) {
//        val resultMap = mutableMapOf<String, Any?>()
//        when (result) {
//            is CardScanSheetResult.Completed -> {
//                resultMap["status"] = "Succeeded"
//                val dataMap = mutableMapOf<String, String?>()
//                dataMap["pan"] = result.scannedCard.pan
//                resultMap["data"] = dataMap
//            }
//            is CardScanSheetResult.Canceled -> {
//                resultMap["status"] = "Cancelled"
//            }
//            is CardScanSheetResult.Failed -> {
//                resultMap["status"] = "Failed"
//            }
//        }
//        callback?.onScanResult(resultMap)
//        finish()
//    }
//}

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