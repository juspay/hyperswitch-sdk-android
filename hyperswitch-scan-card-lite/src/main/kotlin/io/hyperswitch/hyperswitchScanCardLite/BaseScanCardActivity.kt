package io.hyperswitch.hyperswitchScanCardLite

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.hyperswitch.android.hscardscan.cardscan.CardScanSheet
import io.hyperswitch.android.hscardscan.cardscan.CardScanSheetResult

/**
 * Base activity for card scanning functionality that handles the common workflow
 */
abstract class BaseScanCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardScanSheet = CardScanSheet.create(this, ::onScanFinished)
        cardScanSheet.present()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun onScanFinished(result: CardScanSheetResult) {
        processResult(result)
        finish()
    }

    /**
     * Process the scan result - to be implemented by subclasses
     */
    abstract fun processResult(result: CardScanSheetResult)
}
