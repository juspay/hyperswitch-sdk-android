package io.hyperswitch.tapcard

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NFC EMV Card Reader with proper PDOL support for Visa, Mastercard, RuPay.
 *
 * CHANGE LOG_LEVEL to Log.DEBUG for detailed diagnostics
 */
class TapCardReader(
    private val activity: Activity,
    private val config: TapCardConfig,
    listener: TapCardNfcListener? = null
) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val tlvParser = TlvParser()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<TapCardNfcListener>()
    private val isReleased = AtomicBoolean(false)

    init {
        listener?.let { listeners.add(it) }
    }

    companion object {
        private const val TAG = "TapCardReader"
        private const val LOG_LEVEL = Log.DEBUG // Set to DEBUG for diagnostics

        private const val READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        private const val CONNECTION_TIMEOUT_MS = 10000

        // EMV Tags
        private const val TAG_PAN = 0x5A
        private const val TAG_EXPIRY_DATE = 0x5F24
        private const val TAG_TRACK2_EQUIVALENT = 0x57
        private const val TAG_TRACK1_EQUIVALENT = 0x56
        private const val TAG_AFL = 0x94
        private const val TAG_AIP = 0x82
        private const val TAG_FCI_TEMPLATE = 0x6F
        private const val TAG_FCI_PROPRIETARY = 0xA5
        private const val TAG_AID = 0x4F
        private const val TAG_DF_NAME = 0x84
        private const val TAG_APPLICATION_TEMPLATE = 0x61
        private const val TAG_APPLICATION_LABEL = 0x50
        private const val TAG_CARDHOLDER_NAME = 0x5F20
        private const val TAG_ISSUER_COUNTRY = 0x5F28
        private const val TAG_PAN_SEQUENCE = 0x5F34
        private const val TAG_PDOL = 0x9F38
        private const val TAG_CDOL1 = 0x8C
        private const val TAG_GPO_RESPONSE_FORMAT_1 = 0x80
        private const val TAG_GPO_RESPONSE_FORMAT_2 = 0x77
        private const val TAG_SFI_TEMPLATE = 0x70
        private const val TAG_LANGUAGE_PREFERENCE = 0x5F2D
        private const val TAG_SERVICE_CODE = 0x5F30

        // Status Word codes
        private const val SW_SUCCESS = 0x9000
        private const val SW_WARNING = 0x6282
        private const val SW_WARNING_2 = 0x6283
        private const val SW_CLA_NOT_SUPPORTED = 0x6E00
        private const val SW_INS_NOT_SUPPORTED = 0x6D00
        private const val SW_WRONG_LENGTH = 0x6700
        private const val SW_SECURITY_STATUS = 0x6982
        private const val SW_CONDITIONS_NOT_SATISFIED = 0x6985
        private const val SW_WRONG_DATA = 0x6A80
        private const val SW_FUNC_NOT_SUPPORTED = 0x6A81
        private const val SW_FILE_NOT_FOUND = 0x6A82
        private const val SW_RECORD_NOT_FOUND = 0x6A83

        /**
         * Card-specific processing profiles
         */
        enum class CardProfile {
            VISA,
            MASTERCARD,
            AMEX,
            DISCOVER,
            UNIONPAY,
            JCB,
            INTERAC,
            RUPAY,
            GENERIC
        }

        /**
         * Helper to create GPO command manually
         */
        fun createGpoCommand(pdolData83Wrapped: ByteArray): TapCardApduCommand {
            return TapCardApduCommand(
                cla = 0x00.toByte(),
                ins = 0xA8.toByte(),
                p1 = 0x00.toByte(),
                p2 = 0x00.toByte(),
                data = pdolData83Wrapped,
                le = null
            )
        }
    }

    fun isNfcAvailable(): Boolean = nfcAdapter?.isEnabled == true

    fun startReading() {
        if (nfcAdapter?.isEnabled != true) {
            mainHandler.post { notifyListeners { it.onNfcNotAvailable() } }
            return
        }
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        nfcAdapter.enableReaderMode(activity, this, READER_FLAGS, options)
    }

    fun stopReading() {
        try { nfcAdapter?.disableReaderMode(activity) } catch (e: IllegalStateException) {}
    }

    fun release() {
        if (isReleased.compareAndSet(false, true)) {
            stopReading()
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (e: InterruptedException) { executor.shutdownNow() }
            listeners.clear()
        }
    }

    /**
     * Adds a listener to receive NFC card reading events.
     * Multiple listeners can be registered simultaneously.
     * Thread-safe - can be called from any thread.
     */
    fun addListener(listener: TapCardNfcListener) {
        if (!isReleased.get()) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a previously registered listener.
     * Thread-safe - can be called from any thread.
     */
    fun removeListener(listener: TapCardNfcListener) {
        listeners.remove(listener)
    }

    /**
     * Checks if any listeners are currently registered.
     * Thread-safe - can be called from any thread.
     */
    fun hasListeners(): Boolean = listeners.isNotEmpty()

    override fun onTagDiscovered(tag: Tag) {
        log("Tag discovered: ${tag.techList.joinToString()}")
        mainHandler.post { notifyListeners { it.onTagDiscovered(tag) } }

        executor.execute {
            try {
                mainHandler.post { notifyListeners { it.onReadingStarted() } }
                val cardData = readCard(tag)
                mainHandler.post {
                    notifyListeners { it.onCardDetected(cardData) }
                    notifyListeners { it.onCardRemoved() }
                }
            } catch (e: TapCardException) {
                mainHandler.post {
                    notifyListeners { it.onError(e) }
                    notifyListeners { it.onCardRemoved() }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    notifyListeners { it.onError(TapCardException.ConnectionException("Unexpected: ${e.message}", e)) }
                    notifyListeners { it.onCardRemoved() }
                }
            }
        }
    }

    private inline fun notifyListeners(action: (TapCardNfcListener) -> Unit) {
        listeners.forEach(action)
    }

    @Throws(TapCardException::class)
    private fun readCard(tag: Tag): TapCardData {
        val isoDep = IsoDep.get(tag)
            ?: throw TapCardException.ConnectionException("No ISO-DEP support")

        isoDep.timeout = CONNECTION_TIMEOUT_MS

        return try {
            isoDep.connect()
            if (!isoDep.isConnected) throw TapCardException.ConnectionException("Connection failed")

            // Try PPSE first
            val ppseError: TapCardException? = try {
                return tryPPSEFlow(isoDep)
            } catch (e: TapCardException) {
                log("PPSE flow failed: ${e.message}, trying direct AID selection...")
                e
            }

            // Try known AIDs from the Aids companion object
            val aids = TapCardApduCommand.Companion.Aids.ALL_AIDS
            var lastAidError: TapCardException? = null

            for (aid in aids) {
                try {
                    log("Trying direct AID: ${bytesToHex(aid)}")
                    return readWithAid(isoDep, aid.copyOf())
                } catch (e: TapCardException) {
                    lastAidError = e
                    log("AID ${bytesToHex(aid)} failed: ${e.message}")
                    continue
                }
            }

            // All methods failed - throw comprehensive error
            val errorMsg = buildString {
                append("Failed to read card. ")
                ppseError?.let { append("PPSE: ${it.message}. ") }
                lastAidError?.let { append("Last AID attempt: ${it.message}. ") }
                append("Tried ${aids.size} known AIDs.")
            }
            throw TapCardException.EmvDataNotFoundException(errorMsg)
        } finally {
            try { isoDep.close() } catch (e: IOException) {}
        }
    }

    @Throws(TapCardException::class)
    private fun tryPPSEFlow(isoDep: IsoDep): TapCardData {
        log("=== SELECT PPSE ===")
        var ppseResp = transceive(isoDep, TapCardApduCommand.selectPPSE())
        var sw = extractSw(ppseResp)
        log("PPSE SW: ${getSwString(ppseResp)}")
        log("PPSE raw response: ${bytesToHex(ppseResp)}")

        // Retry with different CLA if needed
        if (sw == SW_CLA_NOT_SUPPORTED) {
            log("Retrying PPSE with CLA=0x80...")
            val altCmd = TapCardApduCommand(
                cla = 0x80.toByte(),
                ins = 0xA4.toByte(),
                p1 = 0x04.toByte(),
                p2 = 0x00.toByte(),
                data = TapCardApduCommand.PPSE_FCI_NAME,
                le = null
            )
            ppseResp = transceive(isoDep, altCmd)
            sw = extractSw(ppseResp)
            log("PPSE Retry SW: ${getSwString(ppseResp)}")
        }

        // If we get 0x6700 (Wrong length), the card might need a specific Le byte
        if (sw == SW_WRONG_LENGTH) {
            log("Retrying PPSE with explicit Le=0x00...")
            val altCmd = TapCardApduCommand(
                cla = 0x00.toByte(),
                ins = 0xA4.toByte(),
                p1 = 0x04.toByte(),
                p2 = 0x00.toByte(),
                data = TapCardApduCommand.PPSE_FCI_NAME,
                le = 0x00.toByte()
            )
            ppseResp = transceive(isoDep, altCmd)
            sw = extractSw(ppseResp)
            log("PPSE with Le SW: ${getSwString(ppseResp)}")
        }

        // Check if PPSE selection succeeded
        if (!isSuccessSw(ppseResp)) {
            throw TapCardException.ApduException.fromStatusWord(sw)
        }

        val ppseTlvs = parseResponse(ppseResp)
        log("PPSE TLVs: ${ppseTlvs.map { "0x${it.tag.toString(16)}" }}")

        // Dump all TLVs for debugging
        ppseTlvs.forEach { tlv ->
            log("TLV: 0x${tlv.tag.toString(16)}, len=${tlv.length}, value=${bytesToHex(tlv.value)}")
        }

        // Extract all AIDs
        val aids = mutableListOf<ByteArray>()
        val fciTemplate = ppseTlvs.findTag(TAG_FCI_TEMPLATE)
        log("FCI Template (0x6F): ${fciTemplate != null}")

        if (fciTemplate != null) {
            val fciChildren = try { fciTemplate.parseChildren(tlvParser) } catch (e: Exception) {
                log("Failed to parse FCI children: ${e.message}")
                emptyList()
            }
            log("FCI Children: ${fciChildren.map { "0x${it.tag.toString(16)}" }}")

            // Look for FCI Proprietary Template (0xA5)
            val proprietary = fciChildren.findTag(TAG_FCI_PROPRIETARY)
            log("FCI Proprietary (0xA5): ${proprietary != null}")

            if (proprietary != null) {
                val propChildren = try { proprietary.parseChildren(tlvParser) } catch (e: Exception) {
                    log("Failed to parse proprietary children: ${e.message}")
                    emptyList()
                }
                log("Proprietary Children: ${propChildren.map { "0x${it.tag.toString(16)}" }}")

                // Look for Directory Entry (BF0C) which contains applications
                val dirEntry = propChildren.findTag(0xBF0C)
                log("Directory Entry (0xBF0C): ${dirEntry != null}")

                val appList = if (dirEntry != null) {
                    log("BF0C isConstructed: ${dirEntry.isConstructed()}, value len: ${dirEntry.value.size}")
                    log("BF0C value: ${bytesToHex(dirEntry.value)}")
                    try {
                        val parsed = dirEntry.parseChildren(tlvParser)
                        log("BF0C parsed children: ${parsed.map { "0x${it.tag.toString(16)}" }}")
                        parsed
                    } catch (e: Exception) {
                        log("Failed to parse BF0C children: ${e.message}")
                        emptyList()
                    }
                } else {
                    propChildren
                }

                log("Application List items: ${appList.map { "0x${it.tag.toString(16)}" }}")

                for (elem in appList) {
                    if (elem.tag == TAG_APPLICATION_TEMPLATE) {
                        val appChildren = try { elem.parseChildren(tlvParser) } catch (e: Exception) {
                            log("Failed to parse app template children: ${e.message}")
                            continue
                        }
                        log("App Template Children: ${appChildren.map { "0x${it.tag.toString(16)}" }}")

                        appChildren.findTag(TAG_AID)?.value?.copyOf()?.let {
                            log("Found AID: ${bytesToHex(it)}")
                            aids.add(it)
                        }
                    }
                }

                // Also check direct DF name (0x84)
                propChildren.findTag(TAG_DF_NAME)?.value?.copyOf()?.let {
                    log("Found DF Name: ${bytesToHex(it)}")
                    if (!aids.any { a -> a.contentEquals(it) }) aids.add(it)
                }
            }

            // Alternative: check for application template directly in FCI children
            if (aids.isEmpty()) {
                for (elem in fciChildren) {
                    if (elem.tag == TAG_APPLICATION_TEMPLATE) {
                        val appChildren = try { elem.parseChildren(tlvParser) } catch (e: Exception) { continue }
                        appChildren.findTag(TAG_AID)?.value?.copyOf()?.let {
                            log("Found AID in FCI child: ${bytesToHex(it)}")
                            aids.add(it)
                        }
                    }
                }
            }
        }

        log("Total AIDs found: ${aids.size}")
        if (aids.isEmpty()) {
            throw TapCardException.EmvDataNotFoundException("No AID in PPSE")
        }

        // Try each AID
        var lastError: TapCardException? = null
        for (aid in aids) {
            try {
                return readWithAid(isoDep, aid)
            } catch (e: TapCardException) {
                lastError = e
                continue
            }
        }
        throw lastError ?: TapCardException.EmvDataNotFoundException("All AIDs failed")
    }

    @Throws(TapCardException::class)
    private fun readWithAid(isoDep: IsoDep, aid: ByteArray): TapCardData {
        val aidStr = bytesToHex(aid)
        log("=== SELECT AID: $aidStr ===")

        // SELECT Application
        val selResp = transceive(isoDep, TapCardApduCommand.selectByAid(aid))
        log("SELECT SW: ${getSwString(selResp)}")

        val appTlvs = parseResponse(selResp)
        log("App TLVs: ${appTlvs.map { "0x${it.tag.toString(16)}" }}")

        val appLabel = extractLabel(appTlvs)

        // Find PDOL
        val pdol = findPdol(appTlvs)
        log("PDOL: ${if (pdol != null) "${pdol.size} bytes: ${bytesToHex(pdol)}" else "none"}")

        // Build and send GPO
        return sendGpo(isoDep, pdol, appTlvs, appLabel, aidStr)
    }

    /**
     * Detect card profile from AID for card-specific handling
     */
    private fun detectCardProfile(aidStr: String): CardProfile {
        return when {
            aidStr.startsWith("A000000003") -> CardProfile.VISA
            aidStr.startsWith("A000000004") -> CardProfile.MASTERCARD
            aidStr.startsWith("A000000025") -> CardProfile.AMEX
            aidStr.startsWith("A000000152") -> CardProfile.DISCOVER
            aidStr.startsWith("A000000333") -> CardProfile.UNIONPAY
            aidStr.startsWith("A000000065") -> CardProfile.JCB
            aidStr.startsWith("A000000277") -> CardProfile.INTERAC
            aidStr.startsWith("A000000524") -> CardProfile.RUPAY
            else -> CardProfile.GENERIC
        }
    }

    @Throws(TapCardException::class)
    private fun sendGpo(
        isoDep: IsoDep,
        pdol: ByteArray?,
        appTlvs: List<TlvParser.TlvElement>,
        appLabel: String?,
        aidStr: String
    ): TapCardData {

        log("=== GET PROCESSING OPTIONS ===")
        val profile = detectCardProfile(aidStr)
        log("Card profile: $profile")

        // Build GPO command
        val gpoCmd: TapCardApduCommand
        val gpoDesc: String

        if (pdol != null && pdol.isNotEmpty()) {
            // Parse and build PDOL response
            log("Raw PDOL from card: ${bytesToHex(pdol)}")

            val pdolResponse = buildPdolResponse(pdol, profile)
            log("Built PDOL response: ${bytesToHex(pdolResponse)}")

            // Check if we got the right size (pdolResponse includes 0x83 wrapper = +2 bytes)
            val expectedLen = calculatePdolLength(pdol)
            val actualDataLen = pdolResponse.size - 2 // Subtract 0x83 tag and length byte
            log("Expected PDOL length: $expectedLen, got: $actualDataLen")

            if (actualDataLen != expectedLen) {
                log("WARNING: PDOL length mismatch!")
            }

            // pdolResponse is already wrapped in 0x83 tag by buildPdolResponse
            log("GPO APDU data (83 tag): ${bytesToHex(pdolResponse)}")

            gpoCmd = TapCardApduCommand(
                cla = 0x00.toByte(),
                ins = 0xA8.toByte(),
                p1 = 0x00.toByte(),
                p2 = 0x00.toByte(),
                data = pdolResponse,
                le = null
            )
            gpoDesc = "with PDOL"
        } else {
            // No PDOL - use empty GPO: 83 00
            log("No PDOL, using empty GPO")
            gpoCmd = TapCardApduCommand(
                cla = 0x00.toByte(),
                ins = 0xA8.toByte(),
                p1 = 0x00.toByte(),
                p2 = 0x00.toByte(),
                data = byteArrayOf(0x83.toByte(), 0x00.toByte()),
                le = null
            )
            gpoDesc = "empty"
        }

        // Log full APDU bytes
        val apduBytes = gpoCmd.toByteArray()
        log("Full GPO APDU ($gpoDesc): ${bytesToHex(apduBytes)}")
        log("  CLA: ${apduBytes[0].toInt() and 0xFF}, INS: ${apduBytes[1].toInt() and 0xFF}")
        log("  P1: ${apduBytes[2].toInt() and 0xFF}, P2: ${apduBytes[3].toInt() and 0xFF}")
        log("  Lc: ${apduBytes[4].toInt() and 0xFF}")

        // Send GPO with CLA retry logic
        var gpoResp = transceive(isoDep, gpoCmd)
        var gpoSw = extractSw(gpoResp)
        log("GPO Response: SW=${getSwString(gpoResp)}, len=${gpoResp.size}")

        // Retry with alternate CLA if CLA not supported (0x6E00)
        if (gpoSw == SW_CLA_NOT_SUPPORTED) {
            log("Retrying GPO with CLA=0x80...")
            val altCmd = TapCardApduCommand(
                cla = 0x80.toByte(),
                ins = 0xA8.toByte(),
                p1 = 0x00.toByte(),
                p2 = 0x00.toByte(),
                data = gpoCmd.data,
                le = null
            )
            gpoResp = transceive(isoDep, altCmd)
            gpoSw = extractSw(gpoResp)
            log("GPO Retry Response: SW=${getSwString(gpoResp)}")
        }

        // Check if GPO succeeded
        if (!isSuccessSw(gpoResp)) {
            val error = TapCardException.ApduException.fromStatusWord(gpoSw)

            // Retry with alternate formats for common errors
            // Include SW_WRONG_LENGTH (0x6700) as some cards reject specific data lengths
            if (gpoSw == SW_CLA_NOT_SUPPORTED || gpoSw == SW_INS_NOT_SUPPORTED ||
                gpoSw == SW_WRONG_DATA || gpoSw == SW_WRONG_LENGTH ||
                gpoSw == SW_CONDITIONS_NOT_SATISFIED) {
                log("GPO failed with SW=${getSwString(gpoResp)}, trying fallback strategies...")

                // Try card-specific GPO formats
                val altResp = tryGpoFallbacks(isoDep, gpoCmd.data ?: byteArrayOf(0x83.toByte(), 0x00.toByte()), profile)
                if (isSuccessSw(altResp)) {
                    log("Fallback GPO succeeded!")
                    return processGpoResponse(isoDep, altResp, appTlvs, appLabel, aidStr)
                }

                // If configured, try direct record reading when GPO fails
                if (config.tryDirectReadOnFailure) {
                    // Try reading records directly with common SFI values
                    log("All GPO attempts failed, trying direct record read...")
                    val directCardData = readRecordsDirect(isoDep, appLabel, aidStr)
                    if (directCardData.cardNumber != null) {
                        return directCardData
                    }

                    // Try selective record reading based on card profile
                    log("Trying profile-specific record reading...")
                    val profileData = readRecordsByProfile(isoDep, profile, appLabel, aidStr)
                    if (profileData.cardNumber != null) {
                        return profileData
                    }
                }

                // Last resort: try extracting from SELECT response only
                log("Trying to extract from SELECT response only...")
                val cardData = extractCardData(appTlvs, appLabel, aidStr)
                if (cardData.cardNumber != null) {
                    return cardData
                }

                // Final fallback: skip GPO entirely and use brute force record reading
                log("Final fallback: skipping GPO, using brute-force record reading...")
                val bruteForceData = readRecordsBruteForce(isoDep, appLabel, aidStr)
                if (bruteForceData.cardNumber != null) {
                    return bruteForceData
                }

                // Fall through to throw error
            }

            throw error
        }

        log("GPO SUCCESS: SW=${getSwString(gpoResp)}, response len=${gpoResp.size}")
        return processGpoResponse(isoDep, gpoResp, appTlvs, appLabel, aidStr)
    }

    /**
     * Try various GPO fallback strategies based on card profile
     */
    private fun tryGpoFallbacks(isoDep: IsoDep, originalData: ByteArray, profile: CardProfile): ByteArray {
        val fallbackFormats = mutableListOf<ByteArray>()

        // Strategy 1: Empty GPO (8300) - simplest form, works on many cards
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x00.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = byteArrayOf(0x83.toByte(), 0x00.toByte()),
            le = null
        ).toByteArray())

        // Strategy 2: Empty GPO with CLA 0x80
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x80.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = byteArrayOf(0x83.toByte(), 0x00.toByte()),
            le = null
        ).toByteArray())

        // Strategy 3: 8100 format (some cards expect this)
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x00.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = byteArrayOf(0x81.toByte(), 0x00.toByte()),
            le = null
        ).toByteArray())

        // Strategy 4: Original data with CLA 0x80
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x80.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = originalData,
            le = null
        ).toByteArray())

        // Strategy 5: Minimal TTQ-only GPO (some Visa cards require just this)
        // 83 06 9F 66 04 XX XX XX XX - TTQ with tag-length wrapper
        val ttqOnlyData = byteArrayOf(
            0x83.toByte(), 0x06,  // Tag 83, length 6
            0x9F.toByte(), 0x66.toByte(), 0x04,  // TTQ tag (9F66), length 4
            0x26.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Minimal TTQ value
        )
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x00.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = ttqOnlyData,
            le = null
        ).toByteArray())

        // Strategy 6: Even more minimal - just TTQ value without inner tag wrapper
        val rawTtqData = byteArrayOf(
            0x83.toByte(), 0x04,  // Tag 83, length 4 (raw TTQ value only)
            0x26.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        fallbackFormats.add(TapCardApduCommand(
            cla = 0x00.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = rawTtqData,
            le = null
        ).toByteArray())

        // Card-specific strategies
        when (profile) {
            CardProfile.VISA -> {
                // Visa qVSDC cards often need minimal GPO
                // Try with just TTQ (Terminal Transaction Qualifiers)
                fallbackFormats.add(TapCardApduCommand(
                    cla = 0x00.toByte(),
                    ins = 0xA8.toByte(),
                    p1 = 0x00.toByte(),
                    p2 = 0x00.toByte(),
                    data = byteArrayOf(0x83.toByte(), 0x06, 0x9F.toByte(), 0x66.toByte(), 0x04, 0xF6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte()),
                    le = null
                ).toByteArray())
                // Visa: try with Le=00
                fallbackFormats.add(byteArrayOf(0x00.toByte(), 0xA8.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x83.toByte(), 0x00.toByte(), 0x00.toByte()))

                // Visa cards with strict PDOL requirements
                // Try raw 80-style APDU (Format 1 response)
                fallbackFormats.add(TapCardApduCommand(
                    cla = 0x00.toByte(),
                    ins = 0xA8.toByte(),
                    p1 = 0x00.toByte(),
                    p2 = 0x00.toByte(),
                    data = byteArrayOf(0x80.toByte(), 0x00.toByte()),  // Some Visa cards use 80 instead of 83
                    le = 0x00.toByte()
                ).toByteArray())

                // Try raw binary TTQ (no TLV wrapper) - for very strict cards
                val rawTtq = byteArrayOf(0x26.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
                fallbackFormats.add(TapCardApduCommand(
                    cla = 0x00.toByte(),
                    ins = 0xA8.toByte(),
                    p1 = 0x00.toByte(),
                    p2 = 0x00.toByte(),
                    data = byteArrayOf(0x83.toByte(), rawTtq.size.toByte()) + rawTtq,
                    le = null
                ).toByteArray())
            }
            CardProfile.AMEX -> {
                // Amex sometimes needs 8300 format
                fallbackFormats.add(TapCardApduCommand(
                    cla = 0x00.toByte(),
                    ins = 0xA8.toByte(),
                    p1 = 0x00.toByte(),
                    p2 = 0x00.toByte(),
                    data = byteArrayOf(0x83.toByte(), 0x00.toByte()),
                    le = null
                ).toByteArray())
            }
            CardProfile.UNIONPAY -> {
                // UnionPay sometimes needs different format
                fallbackFormats.add(TapCardApduCommand(
                    cla = 0x00.toByte(),
                    ins = 0xA8.toByte(),
                    p1 = 0x04.toByte(),
                    p2 = 0x00.toByte(),
                    data = byteArrayOf(0x83.toByte(), 0x00.toByte()),
                    le = null
                ).toByteArray())
            }
            else -> {}
        }

        // Try each fallback format
        for ((index, apdu) in fallbackFormats.withIndex()) {
            try {
                log("Trying GPO fallback #$index...")
                val resp = isoDep.transceive(apdu)
                if (isSuccessSw(resp)) {
                    return resp
                }
            } catch (e: Exception) {
                // Continue to next fallback
            }
        }

        return byteArrayOf()
    }

    /**
     * Read records based on card profile-specific knowledge
     */
    private fun readRecordsByProfile(isoDep: IsoDep, profile: CardProfile, appLabel: String?, aidStr: String): TapCardData {
        log("Trying profile-specific reading for $profile...")
        val allTlvs = mutableListOf<TlvParser.TlvElement>()

        // Profile-specific SFI priorities
        val sfiPriority = when (profile) {
            CardProfile.VISA -> listOf(1, 2, 3, 4, 5)
            CardProfile.MASTERCARD -> listOf(1, 2, 3, 4)
            CardProfile.AMEX -> listOf(1, 2, 3)
            CardProfile.UNIONPAY -> listOf(1, 2, 3, 4, 21)
            CardProfile.DISCOVER -> listOf(1, 2, 3)
            CardProfile.JCB -> listOf(1, 2, 3)
            CardProfile.INTERAC -> listOf(1, 2)
            CardProfile.RUPAY -> listOf(1, 2, 3, 4)
            else -> listOf(1, 2, 3, 4, 5)
        }

        // Try prioritized SFIs first
        for (sfi in sfiPriority) {
            for (recNum in 1..5) {
                try {
                    val resp = transceive(isoDep, TapCardApduCommand.readRecord(recNum, sfi))
                    if (isSuccessSw(resp)) {
                        log("  Profile read SFI=$sfi record=$recNum: SUCCESS")
                        val tlvs = parseResponse(resp)
                        allTlvs.addAll(tlvs)

                        // Check if we have enough data
                        val tempData = extractCardData(allTlvs, appLabel, aidStr)
                        if (tempData.cardNumber != null && tempData.expiryDate != null) {
                            return tempData
                        }
                    }
                } catch (e: TapCardException) {
                    // Continue
                }
            }
        }

        return if (allTlvs.isNotEmpty()) {
            extractCardData(allTlvs, appLabel, aidStr)
        } else {
            TapCardData.EMPTY
        }
    }

    private fun calculatePdolLength(pdol: ByteArray): Int {
        var len = 0
        var i = 0
        while (i < pdol.size) {
            // Skip tag bytes
            var tag = pdol[i].toInt() and 0xFF
            i++
            if ((tag and 0x1F) == 0x1F) {
                do {
                    if (i >= pdol.size) break
                    tag = (tag shl 8) or (pdol[i].toInt() and 0xFF)
                    i++
                } while (i < pdol.size && (pdol[i - 1].toInt() and 0x80) != 0)
            }
            // Get length
            if (i >= pdol.size) break
            len += pdol[i].toInt() and 0xFF
            i++
        }
        return len
    }

    @Throws(TapCardException::class)
    private fun processGpoResponse(
        isoDep: IsoDep,
        gpoResp: ByteArray,
        appTlvs: List<TlvParser.TlvElement>,
        appLabel: String?,
        aidStr: String
    ): TapCardData {

        val gpoTlvs = parseResponse(gpoResp)
        log("GPO TLVs: ${gpoTlvs.map { "0x${it.tag.toString(16)}" }}")

        // Extract AFL
        val afl = extractAfl(gpoTlvs)

        return if (afl != null && afl.isNotEmpty()) {
            log("AFL: ${bytesToHex(afl)}")
            try {
                readWithAfl(isoDep, afl, appLabel, aidStr)
            } catch (e: TapCardException.EmvDataNotFoundException) {
                // Try brute force reading
                log("Normal AFL reading failed, trying brute force...")
                val bruteForceData = readRecordsBruteForce(isoDep, appLabel, aidStr)
                if (bruteForceData.cardNumber != null) {
                    bruteForceData
                } else {
                    // Return partial data if we have any
                    val combined = mutableListOf<TlvParser.TlvElement>()
                    combined.addAll(appTlvs)
                    combined.addAll(gpoTlvs)
                    extractCardData(combined, appLabel, aidStr, forceReturn = true)
                }
            }
        } else {
            log("No AFL, extracting from responses...")
            // Extract from combined TLVs
            val combined = mutableListOf<TlvParser.TlvElement>()
            combined.addAll(appTlvs)
            combined.addAll(gpoTlvs)
            extractCardData(combined, appLabel, aidStr)
        }
    }

    @Throws(TapCardException::class)
    private fun readWithAfl(
        isoDep: IsoDep,
        afl: ByteArray,
        appLabel: String?,
        aidStr: String
    ): TapCardData {
        val allTlvs = mutableListOf<TlvParser.TlvElement>()

        var i = 0
        while (i + 3 < afl.size) {
            val sfi = (afl[i].toInt() shr 3) and 0x1F
            val firstRec = afl[i + 1].toInt() and 0xFF
            val lastRec = afl[i + 2].toInt() and 0xFF

            log("Reading SFI=$sfi records $firstRec-$lastRec")

            for (recNum in firstRec..lastRec) {
                try {
                    val resp = transceive(isoDep, TapCardApduCommand.readRecord(recNum, sfi))
                    log("  Record $recNum: ${getSwString(resp)}")

                    if (isSuccessSw(resp)) {
                        val tlvs = parseResponse(resp)
                        log("  Record $recNum TLVs: ${tlvs.map { "0x${it.tag.toString(16)}" }}")
                        // Log each TLV for debugging
                        tlvs.forEach { tlv ->
                            log("    Tag 0x${tlv.tag.toString(16)}, len=${tlv.length}, value=${bytesToHex(tlv.value).take(32)}")
                            if (tlv.isConstructed()) {
                                try {
                                    val children = tlv.parseChildren(tlvParser)
                                    log("    Children: ${children.map { "0x${it.tag.toString(16)}" }}")
                                } catch (e: Exception) {
                                    log("    Failed to parse children: ${e.message}")
                                }
                            }
                        }
                        allTlvs.addAll(tlvs)
                    }
                } catch (e: TapCardException) {
                    continue
                }
            }
            i += 4
        }

        return extractCardData(allTlvs, appLabel, aidStr)
    }

    private fun extractCardData(
        tlvs: List<TlvParser.TlvElement>,
        appLabel: String?,
        aidStr: String,
        forceReturn: Boolean = false
    ): TapCardData {
        log("Extracting from ${tlvs.size} TLVs")

        val pan = findPan(tlvs)
        val expiry = findExpiry(tlvs)

        log("PAN found: ${pan != null}, Expiry found: ${expiry != null}")

        // Detect network from AID
        val network = TapCardApduCommand.Companion.Aids.detectNetwork(aidStr.hexToByteArray())

        // If we have at least some data, or forceReturn is set, return what we have
        if (!forceReturn && pan == null && expiry == null) {
            throw TapCardException.EmvDataNotFoundException("No card data found in records")
        }

        return TapCardData(
            cardNumber = pan,
            expiryDate = expiry,
            cardholderName = findTagString(tlvs, TAG_CARDHOLDER_NAME),
            applicationLabel = appLabel,
            issuerCountryCode = findTagHex(tlvs, TAG_ISSUER_COUNTRY),
            panSequenceNumber = findTagHex(tlvs, TAG_PAN_SEQUENCE),
            aid = aidStr,
            cardNetwork = network
        )
    }

    private fun String.hexToByteArray(): ByteArray {
        val hex = this.replace(" ", "").replace("-", "")
        if (hex.length % 2 != 0) return byteArrayOf()
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    // ============ PDOL Building ============

    /**
     * Build PDOL response data from PDOL template
     * Based on emv_nfc_reader Flutter plugin implementation
     */
    private fun buildPdolResponse(pdol: ByteArray, profile: CardProfile = CardProfile.GENERIC): ByteArray {
        val result = mutableListOf<Byte>()
        var offset = 0

        while (offset < pdol.size) {
            // Parse tag
            var tag = pdol[offset].toInt() and 0xFF
            offset++

            // Multi-byte tag?
            if ((tag and 0x1F) == 0x1F) {
                do {
                    if (offset >= pdol.size) break
                    tag = (tag shl 8) or (pdol[offset].toInt() and 0xFF)
                    offset++
                } while (offset < pdol.size && (pdol[offset - 1].toInt() and 0x80) != 0)
            }

            // Get length
            if (offset >= pdol.size) break
            val len = pdol[offset].toInt() and 0xFF
            offset++

            log("PDOL tag 0x${tag.toString(16)}, len=$len")

            // Add terminal data for this tag with profile-aware values
            val data = getTerminalDataForTag(tag, len, profile)
            result.addAll(data.toList())
        }

        val pdolData = result.toByteArray()
        log("PDOL data: ${bytesToHex(pdolData)}")

        // Wrap in tag 0x83 (Response Message Template Format 1)
        return byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
    }

    /**
     * Provide terminal data for PDOL tags
     * Values based on emv_nfc_reader Flutter plugin with profile-specific enhancements
     */
    private fun getTerminalDataForTag(tag: Int, len: Int, profile: CardProfile = CardProfile.GENERIC): ByteArray {
        return when (tag) {
            // Terminal Transaction Qualifiers (Contactless)
            // Profile-specific TTQ values
            0x9F66 -> when (len) {
                4 -> when (profile) {
                    // Visa: qVSDC + MSD + Online PIN
                    CardProfile.VISA -> byteArrayOf(0xF6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte())
                    // Mastercard: qVSDC + CDA
                    CardProfile.MASTERCARD -> byteArrayOf(0xF6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte())
                    // Amex: ExpressPay specific
                    CardProfile.AMEX -> byteArrayOf(0xB6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte())
                    // UnionPay: UICS
                    CardProfile.UNIONPAY -> byteArrayOf(0xF6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte())
                    // Default
                    else -> byteArrayOf(0xF6.toByte(), 0x20.toByte(), 0xC0.toByte(), 0x00.toByte())
                }
                else -> ByteArray(len) { 0x00 }
            }

            // Amount, Authorized (Binary) - always zero for card reading
            0x9F02 -> ByteArray(len) { 0x00 }

            // Amount, Other (Binary) - always zero for card reading
            0x9F03 -> ByteArray(len) { 0x00 }

            // Terminal Country Code (Configurable - defaults to US)
            0x9F1A -> when (len) {
                2 -> byteArrayOf(0x08.toByte(), 0x40.toByte()) // USA
                else -> ByteArray(len) { 0x00 }
            }

            // Terminal Verification Results - all zero
            0x95 -> ByteArray(len) { 0x00 }

            // Transaction Currency Code (USD = 0840)
            0x5F2A -> when (len) {
                2 -> byteArrayOf(0x08.toByte(), 0x40.toByte()) // USD
                else -> ByteArray(len) { 0x00 }
            }

            // Transaction Date (YYMMDD) - current date would be ideal, using fixed for reliability
            0x9A -> when (len) {
                3 -> byteArrayOf(0x25.toByte(), 0x01.toByte(), 0x01.toByte()) // 2025-01-01
                else -> ByteArray(len) { 0x00 }
            }

            // Transaction Type (00 = Purchase)
            0x9C -> when (len) {
                1 -> byteArrayOf(0x00.toByte())
                else -> ByteArray(len) { 0x00 }
            }

            // Unpredictable Number (Random)
            0x9F37 -> {
                val random = java.security.SecureRandom()
                ByteArray(len) { random.nextInt(256).toByte() }
            }

            // Terminal Capabilities
            0x9F33 -> when (len) {
                3 -> byteArrayOf(0xE0.toByte(), 0xF8.toByte(), 0xC8.toByte())
                else -> ByteArray(len) { 0x00 }
            }

            // Additional Terminal Capabilities
            0x9F40 -> when (len) {
                5 -> byteArrayOf(0xF6.toByte(), 0x00.toByte(), 0xF0.toByte(), 0xA0.toByte(), 0x01.toByte())
                else -> ByteArray(len) { 0x00 }
            }

            // Merchant Name/Location (fill with spaces)
            0x9F4E -> ByteArray(len) { 0x20 }

            // Terminal Identification
            0x9F1C -> when (len) {
                8 -> "TERM0001".toByteArray(Charsets.US_ASCII)
                else -> ByteArray(len) { 0x30 }
            }

            // Interface Device Serial Number
            0x9F1E -> when (len) {
                8 -> "SN123456".toByteArray(Charsets.US_ASCII)
                else -> ByteArray(len) { 0x30 }
            }

            // Application Version Number
            0x9F09 -> when (len) {
                2 -> byteArrayOf(0x00.toByte(), 0x02.toByte())
                else -> ByteArray(len) { 0x00 }
            }

            // Default: zeros
            else -> {
                log("Unknown PDOL tag 0x${tag.toString(16)}, filling with zeros")
                ByteArray(len) { 0x00 }
            }
        }
    }

    // ============ Helpers ============

    private fun findPdol(tlvs: List<TlvParser.TlvElement>): ByteArray? {
        return tlvs.findTagRecursive(TAG_PDOL)?.value?.copyOf()
    }

    private fun extractAfl(tlvs: List<TlvParser.TlvElement>): ByteArray? {
        // Format 1: tag 80 with AFL embedded
        val fmt1 = tlvs.findTag(TAG_GPO_RESPONSE_FORMAT_1)
        if (fmt1 != null && fmt1.value.size >= 4) {
            // Skip AIP (2 bytes), rest is AFL
            return fmt1.value.copyOfRange(2, fmt1.value.size)
        }

        // Format 2: tag 77 with tag 94 inside
        val fmt2 = tlvs.findTag(TAG_GPO_RESPONSE_FORMAT_2)
        if (fmt2 != null) {
            val children = try { fmt2.parseChildren(tlvParser) } catch (e: Exception) { emptyList() }
            children.findTag(TAG_AFL)?.value?.copyOf()?.let { return it }
        }

        // Direct search
        return tlvs.findTag(TAG_AFL)?.value?.copyOf()
    }

    /**
     * Attempts to read card records directly using common SFI values.
     * Used as fallback when GPO is not supported by the card.
     */
    private fun readRecordsDirect(isoDep: IsoDep, appLabel: String?, aidStr: String): TapCardData {
        log("Attempting direct record read with common SFI values...")
        val allTlvs = mutableListOf<TlvParser.TlvElement>()

        // Common SFI values used by many cards
        val commonSfis = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val commonRecords = 1..3

        for (sfi in commonSfis) {
            for (recNum in commonRecords) {
                try {
                    val resp = transceive(isoDep, TapCardApduCommand.readRecord(recNum, sfi))
                    if (isSuccessSw(resp)) {
                        log("  Read SFI=$sfi record=$recNum: SUCCESS")
                        val tlvs = parseResponse(resp)
                        allTlvs.addAll(tlvs)
                    }
                } catch (e: TapCardException) {
                    // Ignore errors for individual records
                }
            }
        }

        log("Direct read collected ${allTlvs.size} TLVs")
        if (allTlvs.isNotEmpty()) {
            val cardData = extractCardData(allTlvs, appLabel, aidStr)
            if (cardData.cardNumber != null) {
                log("Successfully extracted card data from direct record read")
                return cardData
            }
        }

        return TapCardData.EMPTY
    }

    /**
     * Aggressive brute-force record reading for secure cards that don't expose data through normal EMV flow.
     * Tries extended SFI ranges and record numbers to find any available card data.
     */
    private fun readRecordsBruteForce(isoDep: IsoDep, appLabel: String?, aidStr: String): TapCardData {
        log("=== BRUTE FORCE RECORD READING ===")
        val allTlvs = mutableListOf<TlvParser.TlvElement>()

        // Extended SFI range - try more SFIs than usual
        val extendedSfis = 1..20
        // Extended record range
        val extendedRecords = 1..10

        var successfulReads = 0

        for (sfi in extendedSfis) {
            for (recNum in extendedRecords) {
                try {
                    val resp = transceive(isoDep, TapCardApduCommand.readRecord(recNum, sfi))
                    if (isSuccessSw(resp)) {
                        log("  Brute force SFI=$sfi record=$recNum: SUCCESS")
                        val tlvs = parseResponse(resp)
                        if (tlvs.isNotEmpty()) {
                            allTlvs.addAll(tlvs)
                            successfulReads++

                            // Log what we found for debugging
                            tlvs.forEach { tlv ->
                                log("    Tag 0x${tlv.tag.toString(16)} (${getTagName(tlv.tag)}): ${bytesToHex(tlv.value).take(40)}")
                            }

                            // Early exit if we found PAN
                            val tempPan = findPan(allTlvs)
                            if (tempPan != null) {
                                log("Found PAN during brute force, stopping early")
                                return extractCardData(allTlvs, appLabel, aidStr, forceReturn = true)
                            }
                        }
                    }
                } catch (e: TapCardException) {
                    // Expected for invalid SFI/record combinations
                }
            }
        }

        log("Brute force completed: $successfulReads successful reads, ${allTlvs.size} total TLVs")

        return if (allTlvs.isNotEmpty()) {
            extractCardData(allTlvs, appLabel, aidStr, forceReturn = true)
        } else {
            TapCardData.EMPTY
        }
    }

    /**
     * Helper to get human-readable tag names for debugging
     */
    private fun getTagName(tag: Int): String {
        return when (tag) {
            TAG_PAN -> "PAN"
            TAG_EXPIRY_DATE -> "EXPIRY"
            TAG_TRACK2_EQUIVALENT -> "TRACK2"
            TAG_TRACK1_EQUIVALENT -> "TRACK1"
            TAG_AFL -> "AFL"
            TAG_AIP -> "AIP"
            TAG_FCI_TEMPLATE -> "FCI"
            TAG_CARDHOLDER_NAME -> "CARDHOLDER"
            TAG_ISSUER_COUNTRY -> "COUNTRY"
            TAG_APPLICATION_LABEL -> "APP_LABEL"
            TAG_PDOL -> "PDOL"
            0x57 -> "TRACK2_ALT"
            0x5A -> "PAN_ALT"
            0x5F24 -> "EXPIRY_ALT"
            0x5F20 -> "NAME_ALT"
            0x5F28 -> "COUNTRY_ALT"
            0x5F34 -> "PAN_SEQ"
            0x8C -> "CDOL1"
            0x8D -> "CDOL2"
            0x90 -> "ISSUER_PK_CERT"
            0x93 -> "SIGNED_STATIC_APP_DATA"
            0x9F32 -> "ISSUER_PK_EXP"
            0x9F46 -> "ICC_PK_CERT"
            0x9F47 -> "ICC_PK_EXP"
            0x9F4B -> "SIGNED_DYNAMIC_APP_DATA"
            0x9F66 -> "TTQ"
            else -> "0x${tag.toString(16).uppercase()}"
        }
    }

    private fun findPan(tlvs: List<TlvParser.TlvElement>): String? {
        // Try standard PAN tag (0x5A)
        tlvs.findTagRecursive(TAG_PAN)?.value?.let {
            val pan = bytesToHex(it).trimEnd { c -> c == 'F' || c == 'f' }
            if (pan.length >= 13) return pan
        }

        // Try track 2 equivalent (0x57)
        tlvs.findTagRecursive(TAG_TRACK2_EQUIVALENT)?.value?.let {
            parseTrack2Pan(it)?.let { pan -> if (pan.length >= 13) return pan }
        }

        // Try to find PAN in any TLV value that looks like a card number
        // This is an aggressive fallback for secure cards
        for (tlv in tlvs) {
            val value = tlv.value
            if (value.size in 6..10) { // PANs are typically 8 bytes (16 digits)
                val hex = bytesToHex(value)
                // Check if it looks like a PAN (starts with 4, 5, 3, or 6 and has valid length)
                if (hex.matches(Regex("^[0-9]{13,19}$"))) {
                    if (hex.startsWith("4") || hex.startsWith("5") ||
                        hex.startsWith("3") || hex.startsWith("6") ||
                        hex.startsWith("2")) {
                        log("Found potential PAN in tag 0x${tlv.tag.toString(16)}: ${hex.take(6)}****${hex.takeLast(4)}")
                        return hex
                    }
                }
            }
        }

        // Deep search in constructed TLVs
        return deepSearchPan(tlvs)
    }

    /**
     * Deep search for PAN in all TLV children recursively
     */
    private fun deepSearchPan(tlvs: List<TlvParser.TlvElement>): String? {
        for (tlv in tlvs) {
            if (tlv.isConstructed()) {
                try {
                    val children = tlv.parseChildren(tlvParser)
                    findPan(children)?.let { return it }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }
        }
        return null
    }

    private fun findExpiry(tlvs: List<TlvParser.TlvElement>): String? {
        // Try standard expiry tag (0x5F24)
        tlvs.findTagRecursive(TAG_EXPIRY_DATE)?.value?.let {
            parseBcdDate(it)?.let { date -> return date }
        }

        // Try track 2 equivalent (0x57)
        tlvs.findTagRecursive(TAG_TRACK2_EQUIVALENT)?.value?.let {
            parseTrack2Expiry(it)?.let { date -> return date }
        }

        // Deep search in constructed TLVs
        return deepSearchExpiry(tlvs)
    }

    /**
     * Deep search for expiry in all TLV children recursively
     */
    private fun deepSearchExpiry(tlvs: List<TlvParser.TlvElement>): String? {
        for (tlv in tlvs) {
            // Try to parse any 3-byte value as YYMMDD
            if (tlv.value.size == 3) {
                val hex = bytesToHex(tlv.value)
                if (hex.matches(Regex("^[0-9]{6}$"))) {
                    val yy = hex.substring(0, 2)
                    val mm = hex.substring(2, 4)
                    val dd = hex.substring(4, 6) // Day, usually ignored for expiry
                    // Validate month
                    val monthInt = mm.toIntOrNull()
                    if (monthInt != null && monthInt in 1..12) {
                        log("Found potential expiry in tag 0x${tlv.tag.toString(16)}: $mm/$yy")
                        return "$mm/$yy"
                    }
                }
            }

            if (tlv.isConstructed()) {
                try {
                    val children = tlv.parseChildren(tlvParser)
                    findExpiry(children)?.let { return it }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }
        }
        return null
    }

    private fun parseTrack2Pan(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val hex = bytesToHex(data)
        val separatorIndex = hex.indexOf('D')
        return if (separatorIndex > 0) hex.substring(0, separatorIndex) else hex
    }

    private fun parseTrack2Expiry(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val hex = bytesToHex(data)
        val separatorIndex = hex.indexOf('D')
        if (separatorIndex > 0 && separatorIndex + 4 < hex.length) {
            val yy = hex.substring(separatorIndex + 1, separatorIndex + 3)
            val mm = hex.substring(separatorIndex + 3, separatorIndex + 5)
            return "$mm/$yy"
        }
        return null
    }

    private fun parseBcdDate(data: ByteArray): String? {
        if (data.size < 3) return null
        val hex = bytesToHex(data)
        val yy = hex.substring(0, 2)
        val mm = hex.substring(2, 4)
        return "$mm/$yy"
    }

    private fun findTagString(tlvs: List<TlvParser.TlvElement>, tag: Int): String? {
        return tlvs.findTagRecursive(tag)?.value?.toString(Charsets.US_ASCII)?.trim()
    }

    private fun findTagHex(tlvs: List<TlvParser.TlvElement>, tag: Int): String? {
        val value = tlvs.findTagRecursive(tag)?.value
        return if (value != null && value.isNotEmpty()) bytesToHex(value) else null
    }

    private fun extractLabel(tlvs: List<TlvParser.TlvElement>): String? {
        return tlvs.findTagRecursive(TAG_APPLICATION_LABEL)?.value?.toString(Charsets.US_ASCII)
    }

    // ============ Low-level ============

    @Throws(TapCardException::class)
    private fun transceive(isoDep: IsoDep, cmd: TapCardApduCommand): ByteArray {
        return try {
            isoDep.transceive(cmd.toByteArray())
        } catch (e: IOException) {
            throw TapCardException.ConnectionException("Transceive failed", e)
        }
    }

    @Throws(TapCardException::class)
    private fun parseResponse(resp: ByteArray): List<TlvParser.TlvElement> {
        if (resp.size < 2) return emptyList()
        val sw = extractSw(resp)
        if (!isSuccessSw(resp)) {
            throw TapCardException.ApduException.fromStatusWord(sw)
        }
        val data = resp.copyOfRange(0, resp.size - 2)
        return if (data.isEmpty()) emptyList() else tlvParser.parse(data)
    }

    private fun extractSw(resp: ByteArray): Int {
        if (resp.size < 2) return 0x6F00
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp[resp.size - 1].toInt() and 0xFF
        return (sw1 shl 8) or sw2
    }

    private fun isSuccessSw(resp: ByteArray): Boolean {
        val sw = extractSw(resp)
        return sw == 0x9000 || sw == 0x6282 || sw == 0x6283
    }

    private fun getSwString(resp: ByteArray): String {
        return "0x${extractSw(resp).toString(16).padStart(4, '0')}"
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun log(msg: String) {
        if (config.enableDebug) {
            Log.d(TAG, msg)
        }
    }

    private fun List<TlvParser.TlvElement>.findTag(tag: Int): TlvParser.TlvElement? {
        return tlvParser.findTag(this, tag)
    }

    private fun List<TlvParser.TlvElement>.findTagRecursive(tag: Int): TlvParser.TlvElement? {
        return tlvParser.findTagRecursive(this, tag)
    }
}
