package io.hyperswitch.tapcard

/**
 * Builder and constants for EMV APDU (Application Protocol Data Unit) commands.
 * This class provides a type-safe way to construct APDU commands for card communication.
 */
class TapCardApduCommand internal constructor(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray?,
    val le: Byte?
) {

    /**
     * Converts this APDU command to a byte array ready for transmission.
     */
    fun toByteArray(): ByteArray {
        val dataBytes = data
        val hasData = dataBytes != null && dataBytes.isNotEmpty()
        val hasLe = le != null

        val length = 4 + // CLA + INS + P1 + P2
                (if (hasData) 1 + dataBytes!!.size else 0) + // Lc + Data
                (if (hasLe) 1 else 0) // Le

        val result = ByteArray(length)
        var offset = 0

        result[offset++] = cla
        result[offset++] = ins
        result[offset++] = p1
        result[offset++] = p2

        if (hasData) {
            result[offset++] = data!!.size.toByte()
            System.arraycopy(data, 0, result, offset, data.size)
            offset += data.size
        }

        if (hasLe) {
            result[offset] = le!!
        }

        return result
    }

    companion object {
        // Command Class (CLA) bytes
        const val CLA_ISO7816: Byte = 0x00.toByte()
        const val CLA_CHAINING: Byte = 0x10.toByte()

        // Instruction (INS) bytes
        const val INS_SELECT: Byte = 0xA4.toByte()
        const val INS_READ_RECORD: Byte = 0xB2.toByte()
        const val INS_GET_DATA: Byte = 0xCA.toByte()
        const val INS_GET_PROCESSING_OPTIONS: Byte = 0xA8.toByte()
        const val INS_VERIFY: Byte = 0x20.toByte()
        const val INS_GENERATE_AC: Byte = 0xAE.toByte()

        // Selection methods (P1)
        const val P1_SELECT_BY_NAME: Byte = 0x04.toByte()
        const val P1_SELECT_BY_FILE_ID: Byte = 0x00.toByte()
        const val P1_SELECT_PARENT_DF: Byte = 0x03.toByte()

        // Selection options (P2)
        const val P2_FIRST_RECORD: Byte = 0x00.toByte()
        const val P2_NEXT_RECORD: Byte = 0x02.toByte()
        const val P2_RETURN_FCI: Byte = 0x00.toByte()
        const val P2_RETURN_NO_FCI: Byte = 0x0C.toByte()

        // EMV Application Identifiers (AID) and names
        val PPSE_FCI_NAME: ByteArray = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)
        val PSE_FCI_NAME: ByteArray = "1PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)

        // Well-known AIDs for all major card networks
        // RID (5 bytes) + PIX (variable) format per ISO 7816-5
        object Aids {
            // Visa (RID: A000000003)
            val VISA_CREDIT_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x10.toByte(), 0x10.toByte())
            val VISA_ELECTRON = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x20.toByte(), 0x10.toByte())
            val V_PAY = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x20.toByte(), 0x20.toByte())
            val VISA_INTERLINK = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x30.toByte(), 0x10.toByte())
            val VISA_PLUS = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x80.toByte(), 0x10.toByte())
            val VISA_US_COMMON_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0xD1.toByte(), 0x01.toByte())

            // Mastercard (RID: A000000004)
            val MASTERCARD = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x10.toByte(), 0x10.toByte())
            val MAESTRO = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x30.toByte(), 0x60.toByte())
            val MASTERCARD_US_MAESTRO = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x22.toByte(), 0x03.toByte())
            val CIRRUS = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x60.toByte(), 0x00.toByte())
            val MASTERCARD_WORLDPAY = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x99.toByte(), 0x99.toByte())

            // American Express (RID: A000000025)
            val AMEX = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x25.toByte())
            val AMEX_EXPRESS_PAY = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 0x07.toByte(), 0x01.toByte())
            val AMEX_US_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x25.toByte(), 0x02.toByte(), 0x01.toByte(), 0x01.toByte())

            // Discover (RID: A000000152)
            val DISCOVER_DPAS = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x52.toByte(), 0x30.toByte(), 0x10.toByte())
            val DISCOVER_COMMON_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x52.toByte(), 0x40.toByte(), 0x10.toByte())
            val DISCOVER_ELO = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x52.toByte(), 0x50.toByte(), 0x10.toByte())

            // UnionPay (RID: A000000333)
            val UNIONPAY_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x33.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte())
            val UNIONPAY_CREDIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x33.toByte(), 0x01.toByte(), 0x01.toByte(), 0x02.toByte())
            val UNIONPAY_QUASI_CREDIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x33.toByte(), 0x01.toByte(), 0x01.toByte(), 0x03.toByte())
            val UNIONPAY_ELECTRONIC_CASH = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x33.toByte(), 0x01.toByte(), 0x01.toByte(), 0x06.toByte())

            // JCB (RID: A000000065)
            val JCB = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x06.toByte(), 0x5.toByte(), 0x10.toByte(), 0x10.toByte())
            val JCB_J_SPEEDY = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x06.toByte(), 0x5.toByte(), 0x20.toByte(), 0x10.toByte())

            // Interac (RID: A000000277)
            val INTERAC = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x77.toByte(), 0x10.toByte(), 0x10.toByte())
            val INTERAC_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x77.toByte(), 0x10.toByte(), 0x10.toByte(), 0x01.toByte())

            // RuPay (RID: A000000524)
            val RUPAY_CREDIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(), 0x24.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte())
            val RUPAY_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(), 0x24.toByte(), 0x01.toByte(), 0x02.toByte(), 0x01.toByte())

            // GIM-UEMOA (West African Economic and Monetary Union)
            val GIM_UEMOA = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x42.toByte(), 0x10.toByte(), 0x10.toByte())

            // Verve (Nigeria)
            val VERVE = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x71.toByte(), 0x00.toByte(), 0x01.toByte())

            // MIR (Russia)
            val MIR = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x06.toByte(), 0x58.toByte(), 0x10.toByte(), 0x10.toByte())
            val MIR_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x06.toByte(), 0x58.toByte(), 0x10.toByte(), 0x20.toByte())

            // Troy (Turkey)
            val TROY = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x65.toByte(), 0x10.toByte(), 0x10.toByte())
            val TROY_DEBIT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x65.toByte(), 0x20.toByte(), 0x10.toByte())

            // Bancontact (Belgium)
            val BANCONTACT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x54.toByte(), 0x10.toByte())

            // Dankort (Denmark)
            val DANKORT = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x21.toByte(), 0x10.toByte())

            // Cartes Bancaires (France)
            val CARTES_BANCAIRES = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x42.toByte(), 0x10.toByte(), 0x10.toByte())

            // eftpos (Australia)
            val EFTPOS_SAVINGS = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x84.toByte())
            val EFTPOS_CHEQUE = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x85.toByte())

            /**
             * All known AIDs ordered by priority/popularity
             */
            val ALL_AIDS = listOf(
                // Most common cards first
                VISA_CREDIT_DEBIT, VISA_ELECTRON, VISA_US_COMMON_DEBIT, V_PAY,
                MASTERCARD, MAESTRO, MASTERCARD_US_MAESTRO, MASTERCARD_WORLDPAY,
                AMEX, AMEX_EXPRESS_PAY, AMEX_US_DEBIT,
                DISCOVER_DPAS, DISCOVER_COMMON_DEBIT, DISCOVER_ELO,
                UNIONPAY_DEBIT, UNIONPAY_CREDIT, UNIONPAY_QUASI_CREDIT, UNIONPAY_ELECTRONIC_CASH,
                JCB, JCB_J_SPEEDY,
                INTERAC, INTERAC_DEBIT,
                RUPAY_CREDIT, RUPAY_DEBIT,
                MIR, MIR_DEBIT,
                TROY, TROY_DEBIT,
                VERVE,
                GIM_UEMOA,
                BANCONTACT,
                DANKORT,
                CARTES_BANCAIRES,
                EFTPOS_SAVINGS, EFTPOS_CHEQUE
            )

            /**
             * Detects card network name from AID bytes
             */
            fun detectNetwork(aid: ByteArray): String {
                if (aid.size < 5) return "Unknown"

                // Helper to check RID (first 5 bytes)
                fun ridMatches(vararg bytes: Int): Boolean {
                    if (aid.size < bytes.size) return false
                    return bytes.withIndex().all { (index, byte) ->
                        (aid[index].toInt() and 0xFF) == byte
                    }
                }

                return when {
                    // Visa
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x03) -> "Visa"
                    // Mastercard
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x04) -> "Mastercard"
                    // American Express
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x25) -> "American Express"
                    // Discover (6 byte RID)
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x01, 0x52) -> "Discover"
                    // UnionPay
                    ridMatches(0xA0, 0x00, 0x00, 0x03, 0x33) -> "UnionPay"
                    // JCB
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x06, 0x05) -> "JCB"
                    // Interac
                    ridMatches(0xA0, 0x00, 0x00, 0x02, 0x77) -> "Interac"
                    // RuPay
                    ridMatches(0xA0, 0x00, 0x00, 0x05, 0x24) -> "RuPay"
                    // MIR (Russia)
                    ridMatches(0xA0, 0x00, 0x00, 0x06, 0x58) -> "Mir"
                    // Troy (Turkey)
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x65) -> "Troy"
                    // Verve (Nigeria)
                    ridMatches(0xA0, 0x00, 0x00, 0x03, 0x71) -> "Verve"
                    // GIM-UEMOA / Cartes Bancaires share RID
                    ridMatches(0xA0, 0x00, 0x00, 0x00, 0x42) -> {
                        // Check PIX to distinguish
                        if (aid.size >= 7 && (aid[5].toInt() and 0xFF) == 0x20) "GIM-UEMOA"
                        else "Cartes Bancaires"
                    }
                    // Bancontact
                    ridMatches(0xA0, 0x00, 0x00, 0x01, 0x54) -> "Bancontact"
                    // Dankort
                    ridMatches(0xA0, 0x00, 0x00, 0x01, 0x21) -> "Dankort"
                    // eftpos
                    ridMatches(0xA0, 0x00, 0x00, 0x03, 0x84) -> "eftpos Savings"
                    ridMatches(0xA0, 0x00, 0x00, 0x03, 0x85) -> "eftpos Cheque"
                    else -> "Unknown"
                }
            }
        }

        // EMV Tag identifiers
        const val TAG_PAN: Byte = 0x5A.toByte()                    // 5A - Primary Account Number
        const val TAG_PAN_SEQUENCE: Byte = 0x5F34.toByte()        // 5F34 - Application PAN Sequence Number
        const val TAG_EXPIRY_DATE: Byte = 0x5F24.toByte()         // 5F24 - Application Expiration Date
        const val TAG_TRACK2_EQUIVALENT: Byte = 0x57.toByte()     // 57 - Track 2 Equivalent Data
        const val TAG_CARDHOLDER_NAME: Byte = 0x5F20.toByte()     // 5F20 - Cardholder Name
        const val TAG_ISSUER_COUNTRY: Byte = 0x5F28.toByte()      // 5F28 - Issuer Country Code
        const val TAG_APPLICATION_LABEL: Byte = 0x50.toByte()     // 50 - Application Label
        const val TAG_APP_PREFERRED_NAME: Byte = 0x9F12.toByte()  // 9F12 - Application Preferred Name
        const val TAG_PRIORITY_INDICATOR: Byte = 0x87.toByte()    // 87 - Priority Indicator
        const val TAG_FCI_TEMPLATE: Byte = 0x6F.toByte()          // 6F - FCI Template
        const val TAG_DF_NAME: Byte = 0x84.toByte()               // 84 - Dedicated File (DF) Name
        const val TAG_FCI_PROPRIETARY: Byte = 0xA5.toByte()       // A5 - FCI Proprietary Template
        const val TAG_FCI_ISSUER: Byte = 0xBF0C.toByte()          // BF0C - FCI Issuer Discretionary Data
        const val TAG_AID: Byte = 0x4F.toByte()                   // 4F - Application Identifier (AID)
        const val TAG_PDOL: Byte = 0x9F38.toByte()                // 9F38 - Processing Options Data Object List
        const val TAG_AFL: Byte = 0x94.toByte()                   // 94 - Application File Locator
        const val TAG_AIP: Byte = 0x82.toByte()                   // 82 - Application Interchange Profile
        const val TAG_RMTF1: Byte = 0x70.toByte()                 // 70 - Read Record Response Template
        const val TAG_TEMPLATE_61: Byte = 0x61.toByte()           // 61 - Application Template
        const val TAG_TEMPLATE_77: Byte = 0x77.toByte()           // 77 - Response Message Template Format 2
        const val TAG_GPO_FORMAT_1: Byte = 0x80.toByte()          // 80 - GPO Response Format 1 (AIP + AFL)

        // Get Processing Options PDOL values
        val PDOL_DEFAULT: ByteArray = byteArrayOf(
            0x83.toByte(), 0x00 // Terminal capabilities (empty)
        )

        /**
         * Creates a SELECT command by name (FCI).
         *
         * @param name The name to select (e.g., PPSE name)
         * @param p1 Selection method, defaults to select by name
         * @param p2 Return options, defaults to return FCI
         */
        fun selectByName(
            name: ByteArray,
            p1: Byte = P1_SELECT_BY_NAME,
            p2: Byte = P2_RETURN_FCI
        ): TapCardApduCommand {
            return TapCardApduCommand(
                cla = CLA_ISO7816,
                ins = INS_SELECT,
                p1 = p1,
                p2 = p2,
                data = name,
                le = 0x00.toByte() // Le = 0 means expect max response (256 bytes)
            )
        }

        /**
         * Creates a SELECT PPSE command for contactless cards.
         */
        fun selectPPSE(): TapCardApduCommand = selectByName(PPSE_FCI_NAME)

        /**
         * Creates a SELECT PSE command for contact cards.
         */
        fun selectPSE(): TapCardApduCommand = selectByName(PSE_FCI_NAME)

        /**
         * Creates a SELECT command by AID.
         *
         * @param aid The Application Identifier to select
         */
        fun selectByAid(aid: ByteArray): TapCardApduCommand {
            return selectByName(aid, P1_SELECT_BY_NAME, P2_RETURN_FCI)
        }

        /**
         * Creates a GET PROCESSING OPTIONS command with raw data.
         * Data should already be wrapped in 0x83 tag if needed.
         *
         * @param data The GPO data (should include 0x83 tag wrapper)
         */
        fun getProcessingOptions(data: ByteArray): TapCardApduCommand {
            return TapCardApduCommand(
                cla = CLA_ISO7816,
                ins = INS_GET_PROCESSING_OPTIONS,
                p1 = 0x00,
                p2 = 0x00,
                data = data,
                le = null
            )
        }

        /**
         * Creates a GET PROCESSING OPTIONS command with PDOL data.
         * Automatically wraps PDOL data in 0x83 tag.
         *
         * @param pdolData The raw PDOL response data (will be wrapped in 0x83)
         */
        fun getProcessingOptionsWithPdol(pdolData: ByteArray): TapCardApduCommand {
            val data = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
            return getProcessingOptions(data)
        }

        /**
         * Creates an empty GET PROCESSING OPTIONS command.
         */
        fun getProcessingOptionsEmpty(): TapCardApduCommand {
            return getProcessingOptions(byteArrayOf(0x83.toByte(), 0x00.toByte()))
        }

        /**
         * Creates a READ RECORD command.
         *
         * @param recordNumber The record number to read
         * @param sfi The Short File Identifier (SFI)
         */
        fun readRecord(recordNumber: Int, sfi: Int): TapCardApduCommand {
            // P1 = record number, P2 = (SFI << 3) | 4 (P1 is record number)
            val p2 = ((sfi shl 3) or 0x04).toByte()

            return TapCardApduCommand(
                cla = CLA_ISO7816,
                ins = INS_READ_RECORD,
                p1 = recordNumber.toByte(),
                p2 = p2,
                data = null,
                le = 0x00 // Request maximum response
            )
        }

        /**
         * Creates a GET DATA command to retrieve specific EMV data.
         *
         * @param tag The tag to retrieve (1 or 2 bytes)
         */
        fun getData(tag: Int): TapCardApduCommand {
            val p1 = ((tag shr 8) and 0xFF).toByte()
            val p2 = (tag and 0xFF).toByte()

            return TapCardApduCommand(
                cla = CLA_ISO7816,
                ins = INS_GET_DATA,
                p1 = p1,
                p2 = p2,
                data = null,
                le = 0x00
            )
        }
    }
}

/**
 * Helper extension for combining byte arrays.
 */
private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(this.size + other.size)
    System.arraycopy(this, 0, result, 0, this.size)
    System.arraycopy(other, 0, result, this.size, other.size)
    return result
}
