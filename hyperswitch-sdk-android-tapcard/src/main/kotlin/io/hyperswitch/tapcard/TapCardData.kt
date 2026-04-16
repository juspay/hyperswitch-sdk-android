package io.hyperswitch.tapcard

/**
 * Data class representing ALL extracted EMV card data.
 *
 * @property cardNumber The Primary Account Number (PAN)
 * @property expiryDate The card expiry date in MM/YY format
 * @property cardholderName The cardholder name (if available - usually blank on contactless)
 * @property applicationLabel The application label (e.g., "VISA CREDIT")
 * @property issuerCountryCode The ISO 3166 issuer country code
 * @property panSequenceNumber The PAN sequence number
 * @property aid The Application Identifier (AID) in hex format
 * @property cardNetwork The detected card network (Visa, Mastercard, Amex, etc.)
 */
data class TapCardData(
    val cardNumber: String? = null,
    val expiryDate: String? = null,
    val cardholderName: String? = null,
    val applicationLabel: String? = null,
    val issuerCountryCode: String? = null,
    val panSequenceNumber: String? = null,
    val aid: String? = null,
    val cardNetwork: String? = null
) {
    /**
     * The card number with middle digits masked for display.
     * Format: "4111 **** **** 1111"
     * Returns empty string if no card number is available.
     */
    val maskedCardNumber: String
        get() = maskCardNumber(cardNumber)

    /**
     * The card network name (e.g., "Visa", "Mastercard", "American Express").
     * Derived from the AID if cardNetwork is not explicitly set.
     */
    val network: String?
        get() = cardNetwork ?: detectNetworkFromAid()

    /**
     * The expiry month (MM) extracted from expiryDate.
     * Returns null if expiryDate is not in expected format.
     */
    val month: String?
        get() = extractMonth(expiryDate)

    /**
     * The expiry year (YY) extracted from expiryDate.
     * Returns null if expiryDate is not in expected format.
     */
    val year: String?
        get() = extractYear(expiryDate)

    /**
     * The CVC/CVV code.
     * Note: Contactless NFC cards do not expose CVC via the EMV protocol.
     * This always returns null for security reasons.
     */
    val cvc: String?
        get() = null

    /**
     * The issuer country code in human-readable format.
     * Returns the issuerCountryCode as-is.
     */
    val country: String?
        get() = issuerCountryCode

    /**
     * Checks if minimum required fields are present.
     */
    fun isComplete(): Boolean {
        return !cardNumber.isNullOrBlank() && !expiryDate.isNullOrBlank()
    }

    /**
     * Returns true if any data was successfully read from the card.
     */
    fun hasAnyData(): Boolean {
        return !cardNumber.isNullOrBlank() ||
                !expiryDate.isNullOrBlank() ||
                !cardholderName.isNullOrBlank() ||
                !applicationLabel.isNullOrBlank()
    }

    @Deprecated("Use maskedCardNumber property instead", ReplaceWith("maskedCardNumber"))
    fun getMaskedPan(): String = maskedCardNumber

    /**
     * Masks a card number, showing only first 4 and last 4 digits.
     * Formats as: "5242 **** **** 9195"
     */
    private fun maskCardNumber(pan: String?): String {
        if (pan.isNullOrBlank()) return ""
        if (pan.length <= 8) return pan

        val firstFour = pan.take(4)
        val lastFour = pan.takeLast(4)
        val middleLen = pan.length - 8

        // Create masked middle with groups of 4 asterisks
        val groups = middleLen / 4
        val remainder = middleLen % 4

        val sb = StringBuilder()
        sb.append(firstFour)

        // Add asterisk groups
        repeat(groups) {
            sb.append(" ****")
        }
        // Add remainder asterisks
        if (remainder > 0) {
            sb.append(" ")
            repeat(remainder) { sb.append("*") }
        }

        sb.append(" ").append(lastFour)
        return sb.toString()
    }

    /**
     * Detects card network from the AID (Application Identifier).
     */
    private fun detectNetworkFromAid(): String? {
        if (aid.isNullOrBlank() || aid.length < 10) return null

        val rid = aid.take(10).uppercase()
        return when (rid) {
            "A000000003" -> "Visa"
            "A000000004" -> "Mastercard"
            "A000000025" -> "American Express"
            "A000000152" -> "Discover"
            "A000000333" -> "UnionPay"
            "A000000065" -> "JCB"
            "A000000277" -> "Interac"
            "A000000524" -> "RuPay"
            else -> null
        }
    }

    /**
     * Extracts month from expiry date in MM/YY format.
     */
    private fun extractMonth(expiry: String?): String? {
        if (expiry.isNullOrBlank()) return null
        return expiry.split("/").getOrNull(0)?.takeIf { it.length == 2 }
    }

    /**
     * Extracts year from expiry date in MM/YY format.
     */
    private fun extractYear(expiry: String?): String? {
        if (expiry.isNullOrBlank()) return null
        return expiry.split("/").getOrNull(1)?.takeIf { it.length == 2 }
    }

    companion object {
        /**
         * Empty instance representing no data extracted.
         */
        val EMPTY = TapCardData()
    }
}
