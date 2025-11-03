package io.hyperswitch.scancard

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.hyperswitch.android.hscardscan.cardscan.CardScanSheet
import io.hyperswitch.android.hscardscan.cardscan.CardScanSheetResult

class ScanCardFragment: Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardScanSheet = CardScanSheet.create(this, { result: CardScanSheetResult ->
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

                ScanCardManager.getCallback()?.onScanResult(resultMap)
            }
        )
        cardScanSheet.present()
    }
}