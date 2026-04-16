package io.hyperswitch.tapcard

/**
 * Result of checking and requesting NFC permission.
 */
sealed class PermissionResult {
    /**
     * NFC permission is available and NFC is enabled.
     */
    object Success : PermissionResult()

    /**
     * NFC permission check or request failed.
     *
     * @property code The specific error code indicating why permission was not granted
     */
    data class Failed(val code: PermissionError) : PermissionResult()
}

/**
 * Error codes for permission-related failures.
 */
enum class PermissionError {
    /**
     * The NFC permission is not granted in the app manifest.
     */
    PERMISSION_NOT_GRANTED,

    /**
     * The user denied the permission request (on Android versions that require runtime permission).
     */
    USER_DENIED,

    /**
     * NFC is available on the device but is currently disabled in system settings.
     */
    NFC_DISABLED,

    /**
     * NFC hardware is not available on this device.
     */
    NFC_NOT_AVAILABLE
}

/**
 * Error codes for card reading failures.
 */
enum class CardErrorCode {
    /**
     * Card data was successfully read.
     */
    SUCCESS,

    /**
     * The card is restricted and doesn't expose data via NFC.
     * Common for some debit cards with enhanced security.
     */
    CARD_RESTRICTED,

    /**
     * The card type is not supported by this reader.
     */
    UNSUPPORTED_CARD,

    /**
     * The tag detected is not an EMV payment card.
     */
    TAG_NOT_SUPPORTED,

    /**
     * NFC is currently disabled.
     */
    NFC_DISABLED,

    /**
     * A general read failure occurred.
     */
    READ_FAILED
}

/**
 * Result of attempting to read a card via NFC.
 */
sealed class CardResult {
    /**
     * A card was successfully detected and read.
     *
     * @property data The extracted card data including PAN, expiry, network, etc.
     */
    data class CardDetected(val data: TapCardData) : CardResult()

    /**
     * An NFC tag was detected but it's not a supported EMV payment card.
     */
    object TagNotSupported : CardResult()

    /**
     * NFC is currently disabled on the device.
     */
    object NfcDisabled : CardResult()

    /**
     * Failed to read the card due to an error.
     *
     * @property error The exception describing what went wrong
     */
    data class FailedToRead(val error: TapCardException) : CardResult()
}
