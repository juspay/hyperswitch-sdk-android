package io.hyperswitch.tapcard

/**
 * Base exception class for all NFC EMV related errors.
 * Uses sealed class hierarchy for type-safe error handling.
 */
sealed class TapCardException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Exception thrown when NFC is not available or not enabled on the device.
     */
    class NfcNotAvailableException(message: String) : TapCardException(message)

    /**
     * Exception thrown when the connection to the card fails or is lost.
     */
    class ConnectionException(message: String, cause: Throwable? = null) : TapCardException(message, cause)

    /**
     * Exception thrown when an APDU command transmission fails or returns an error SW.
     */
    class ApduException(
        message: String,
        val sw: Int? = null
    ) : TapCardException(message) {
        companion object {
            fun fromStatusWord(sw: Int): ApduException {
                val message = when (sw)
                {
                    SW_OK -> "Success"
                    SW_FILE_NOT_FOUND -> "File not found"
                    SW_WRONG_LENGTH -> "Wrong length"
                    SW_SECURITY_STATUS_NOT_SATISFIED -> "Security status not satisfied"
                    SW_CONDITIONS_NOT_SATISFIED -> "Conditions of use not satisfied"
                    SW_COMMAND_NOT_ALLOWED -> "Command not allowed"
                    SW_WRONG_DATA -> "Wrong data"
                    SW_APPLET_SELECT_FAILED -> "Applet selection failed"
                    SW_WRONG_P1P2 -> "Wrong P1/P2 parameters"
                    SW_INS_NOT_SUPPORTED -> "INS not supported"
                    SW_CLA_NOT_SUPPORTED -> "CLA not supported"
                    else -> "Unknown error SW=0x${sw.toString(16).padStart(4, '0')}"
                }
                return ApduException(message, sw)
            }
        }
    }

    /**
     * Exception thrown when TLV parsing fails.
     */
    class TlvParsingException(message: String, cause: Throwable? = null) : TapCardException(message, cause)

    /**
     * Exception thrown when required EMV data is not found in the response.
     */
    class EmvDataNotFoundException(message: String) : TapCardException(message)

    /**
     * Exception thrown when card reading times out.
     */
    class TimeoutException(message: String) : TapCardException(message)

    /**
     * Exception thrown when the card operation is cancelled.
     */
    class OperationCancelledException(message: String) : TapCardException(message)

    companion object {
        // APDU Response Status Words (SW)
        const val SW_OK: Int = 0x9000
        const val SW_FILE_NOT_FOUND: Int = 0x6A82
        const val SW_WRONG_LENGTH: Int = 0x6700
        const val SW_SECURITY_STATUS_NOT_SATISFIED: Int = 0x6982
        const val SW_CONDITIONS_NOT_SATISFIED: Int = 0x6985
        const val SW_COMMAND_NOT_ALLOWED: Int = 0x6986
        const val SW_WRONG_DATA: Int = 0x6A80
        const val SW_APPLET_SELECT_FAILED: Int = 0x6999
        const val SW_WRONG_P1P2: Int = 0x6B00
        const val SW_INS_NOT_SUPPORTED: Int = 0x6D00
        const val SW_CLA_NOT_SUPPORTED: Int = 0x6E00
        const val SW_RESPONSE_BYTES_AVAILABLE: Int = 0x6100
        const val SW_END_OF_FILE: Int = 0x6282
    }
}
