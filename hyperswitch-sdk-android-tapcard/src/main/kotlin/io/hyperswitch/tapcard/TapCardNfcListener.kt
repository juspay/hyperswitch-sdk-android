package io.hyperswitch.tapcard

import android.nfc.Tag

/**
 * Callback interface for NFC EMV card detection events.
 * Implement this interface to receive card reading results.
 *
 * All callbacks are invoked on the thread that triggers the NFC event
 * (typically the main/UI thread for ReaderCallback).
 */
interface TapCardNfcListener {

    /**
     * Called when a card is detected and successfully read.
     *
     * @param cardData The extracted EMV card data
     */
    fun onCardDetected(cardData: TapCardData)

    /**
     * Called when card reading fails due to an error.
     *
     * @param error The exception describing what went wrong
     */
    fun onError(error: TapCardException)

    /**
     * Called when the NFC tag is discovered but before reading starts.
     * Can be used for UI updates.
     *
     * @param tag The discovered NFC tag
     */
    fun onTagDiscovered(tag: Tag) {}

    /**
     * Called when the reading process starts.
     * Can be used to show progress indicators.
     */
    fun onReadingStarted() {}

    /**
     * Called when the card is removed or the session ends.
     */
    fun onCardRemoved() {}

    /**
     * Called when NFC is not available or disabled on the device.
     */
    fun onNfcNotAvailable() {}
}

/**
 * Adapter class providing default no-op implementations of [TapCardNfcListener].
 * Extend this class to implement only the callbacks you need.
 */
abstract class TapCardNfcListenerAdapter : TapCardNfcListener {
    abstract override fun onCardDetected(cardData: TapCardData)
    abstract override fun onError(error: TapCardException)
    override fun onTagDiscovered(tag: Tag) {}
    override fun onReadingStarted() {}
    override fun onCardRemoved() {}
    override fun onNfcNotAvailable() {}
}
