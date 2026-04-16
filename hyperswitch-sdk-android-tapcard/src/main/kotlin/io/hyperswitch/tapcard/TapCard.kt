package io.hyperswitch.tapcard

import android.app.Activity
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Public-facing API for NFC card reading (TapCard).
 *
 * This class provides a simplified interface for merchants to integrate
 * NFC card reading functionality into their Android applications.
 *
 * Example usage:
 * ```kotlin
 * val tapCardConfig = TapCardConfigBuilder()
 *     .setTimeout(30_000)
 *     .enableDebug(BuildConfig.DEBUG)
 *     .build()
 *
 * val tapCard = TapCard(this, tapCardConfig)
 *
 * tapCard.checkAndRequestPermission { result ->
 *     when (result) {
 *         is PermissionResult.Success -> {
 *             tapCard.startListening { cardResult ->
 *                 when (cardResult) {
 *                     is CardResult.CardDetected -> {
 *                         val data = cardResult.data
 *                         println("Card: ${data.maskedCardNumber}")
 *                         println("Expiry: ${data.expiry}")
 *                     }
 *                     is CardResult.TagNotSupported -> { }
 *                     is CardResult.NfcDisabled -> { }
 *                     is CardResult.FailedToRead -> { }
 *                 }
 *             }
 *         }
 *         is PermissionResult.Failed -> {
 *             when (result.code) {
 *                 PermissionError.NFC_NOT_AVAILABLE -> { }
 *                 PermissionError.NFC_DISABLED -> { }
 *                 else -> { }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param activity The host activity for NFC operations
 * @param config Configuration options for card reading behavior
 */
class TapCard(
    private val activity: Activity,
    private val config: TapCardConfig = TapCardConfig()
) {
    private val reader: TapCardReader = TapCardReader(activity, config)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val isListening = AtomicBoolean(false)
    private val currentListener = AtomicReference<TapCardNfcListener?>(null)
    private val timeoutRunnable = AtomicReference<Runnable?>(null)

    companion object {
        private const val TAG = "TapCard"
    }

    /**
     * Checks if NFC is available and enabled on the device.
     *
     * @return true if NFC hardware is present and enabled, false otherwise
     */
    fun isAvailable(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    /**
     * Checks NFC permission and availability, invoking the callback with the result.
     *
     * Note: NFC permission (android.permission.NFC) is a normal permission that is
     * granted at install time. This method primarily checks:
     * 1. If NFC hardware is available on the device
     * 2. If NFC is enabled in system settings
     *
     * @param callback Invoked with the permission check result on the main thread
     */
    fun checkAndRequestPermission(callback: (PermissionResult) -> Unit) {
        when {
            !hasNfcHardware() -> {
                log("NFC hardware not available")
                postToMain { callback(PermissionResult.Failed(PermissionError.NFC_NOT_AVAILABLE)) }
            }
            !hasNfcPermission() -> {
                log("NFC permission not granted")
                postToMain { callback(PermissionResult.Failed(PermissionError.PERMISSION_NOT_GRANTED)) }
            }
            !isNfcEnabled() -> {
                log("NFC is disabled")
                postToMain { callback(PermissionResult.Failed(PermissionError.NFC_DISABLED)) }
            }
            else -> {
                log("NFC permission granted and enabled")
                postToMain { callback(PermissionResult.Success) }
            }
        }
    }

    /**
     * Starts listening for NFC card taps.
     *
     * This enables NFC reader mode and begins scanning for EMV payment cards.
     * The callback will be invoked when a card is detected, an error occurs,
     * or NFC becomes unavailable.
     *
     * @param callback Invoked with card reading results on the main thread
     */
    fun startListening(callback: (CardResult) -> Unit) {
        if (!isAvailable()) {
            log("Cannot start listening - NFC not available")
            postToMain { callback(CardResult.NfcDisabled) }
            return
        }

        if (isListening.get()) {
            log("Already listening, stopping previous session")
            stopListening()
        }

        log("Starting NFC card listening")
        isListening.set(true)

        // Create internal listener that maps to public CardResult
        val internalListener = object : TapCardNfcListener {
            override fun onCardDetected(cardData: TapCardData) {
                log("Card detected: ${cardData.maskedCardNumber}")
                if (isListening.compareAndSet(true, false)) {
                    cancelTimeout()
                    reader.stopReading()
                    // Remove listener on result
                    currentListener.getAndSet(null)?.let { listener ->
                        reader.removeListener(listener)
                    }
                    postToMain { callback(CardResult.CardDetected(cardData)) }
                }
            }

            override fun onError(error: TapCardException) {
                log("Card reading error: ${error.message}")

                val result = when (error) {
                    is TapCardException.NfcNotAvailableException -> CardResult.NfcDisabled
                    is TapCardException.ConnectionException -> CardResult.FailedToRead(error)
                    is TapCardException.EmvDataNotFoundException -> CardResult.TagNotSupported
                    else -> CardResult.FailedToRead(error)
                }

                // Check if we should continue listening on unsupported tags
                if (result is CardResult.TagNotSupported && config.continueOnTagNotSupported) {
                    log("Tag not supported, but continuing to listen...")
                    // Notify callback but DON'T stop listening
                    postToMain { callback(result) }
                    // Reset for next read - cancel timeout but stay listening
                    cancelTimeout()
                    // Restart timeout if configured
                    restartTimeout(callback)
                    return
                }

                // Normal flow - stop listening on error
                if (isListening.compareAndSet(true, false)) {
                    cancelTimeout()
                    reader.stopReading()
                    // Remove listener on result
                    currentListener.getAndSet(null)?.let { listener ->
                        reader.removeListener(listener)
                    }
                    postToMain { callback(result) }
                }
            }

            override fun onNfcNotAvailable() {
                log("NFC became unavailable")
                if (isListening.compareAndSet(true, false)) {
                    cancelTimeout()
                    reader.stopReading()
                    // Remove listener on result
                    currentListener.getAndSet(null)?.let { listener ->
                        reader.removeListener(listener)
                    }
                    postToMain { callback(CardResult.NfcDisabled) }
                }
            }
        }

        currentListener.set(internalListener)
        reader.addListener(internalListener)
        reader.startReading()

        // Set up timeout if configured
        setupTimeout(callback)
    }

    private fun setupTimeout(callback: (CardResult) -> Unit) {
        if (config.timeoutMs > 0) {
            val runnable = Runnable {
                if (isListening.compareAndSet(true, false)) {
                    log("Card reading timed out after ${config.timeoutMs}ms")
                    stopListening()
                    val timeoutError = TapCardException.TimeoutException(
                        "Card reading timed out after ${config.timeoutMs}ms"
                    )
                    postToMain { callback(CardResult.FailedToRead(timeoutError)) }
                }
            }
            timeoutRunnable.set(runnable)
            mainHandler.postDelayed(runnable, config.timeoutMs)
        }
    }

    private fun restartTimeout(callback: (CardResult) -> Unit) {
        if (config.timeoutMs > 0) {
            val runnable = Runnable {
                if (isListening.compareAndSet(true, false)) {
                    log("Card reading timed out after ${config.timeoutMs}ms")
                    stopListening()
                    val timeoutError = TapCardException.TimeoutException(
                        "Card reading timed out after ${config.timeoutMs}ms"
                    )
                    postToMain { callback(CardResult.FailedToRead(timeoutError)) }
                }
            }
            timeoutRunnable.set(runnable)
            mainHandler.postDelayed(runnable, config.timeoutMs)
        }
    }

    /**
     * Stops listening for NFC card taps.
     *
     * This disables NFC reader mode and cleans up resources.
     * Safe to call even if not currently listening.
     */
    fun stopListening() {
        log("Stopping NFC card listening")
        isListening.set(false)
        cancelTimeout()
        currentListener.getAndSet(null)?.let { listener ->
            reader.removeListener(listener)
        }
        reader.stopReading()
    }

    /**
     * Releases all resources associated with this TapCard instance.
     *
     * Call this when the activity/fragment is destroyed to prevent memory leaks.
     */
    fun release() {
        log("Releasing TapCard resources")
        stopListening()
        reader.release()
    }

    private fun cancelTimeout() {
        timeoutRunnable.getAndSet(null)?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
    }

    private fun hasNfcHardware(): Boolean {
        return nfcAdapter != null
    }

    private fun hasNfcPermission(): Boolean {
        return activity.packageManager.checkPermission(
            android.Manifest.permission.NFC,
            activity.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    private fun postToMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun log(message: String) {
        if (config.enableDebug) {
            Log.d(TAG, message)
        }
    }
}