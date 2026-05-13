package io.hyperswitch.tapcard

/**
 * TLV (Tag-Length-Value) parser for EMV data structures.
 * Handles EMV-compliant BER-TLV encoding.
 */
class TlvParser {

    /**
     * Represents a parsed TLV element.
     *
     * @property tag The tag identifier
     * @property length The value length
     * @property value The value bytes
     * @property raw The raw TLV bytes including tag and length
     */
    data class TlvElement(
        val tag: Int,
        val length: Int,
        val value: ByteArray,
        val raw: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TlvElement) return false
            return tag == other.tag &&
                    length == other.length &&
                    value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            var result = tag
            result = 31 * result + length
            result = 31 * result + value.contentHashCode()
            return result
        }

        /**
         * Returns true if this element has a constructed tag (contains nested TLVs).
         * For multi-byte tags, only the first byte determines if it's constructed.
         */
        fun isConstructed(): Boolean {
            // Get the first byte of the tag (handles multi-byte tags correctly)
            val firstByte = when {
                tag > 0xFFFF -> (tag shr 16) and 0xFF
                tag > 0xFF -> (tag shr 8) and 0xFF
                else -> tag and 0xFF
            }
            return (firstByte and 0x20) != 0
        }

        /**
         * Parses the value as a list of child TLV elements.
         * Only valid for constructed tags.
         */
        fun parseChildren(parser: TlvParser): List<TlvElement> {
            if (!isConstructed()) return emptyList()
            return parser.parse(value)
        }
    }

    /**
     * Parses a byte array containing TLV-encoded data.
     *
     * @param data The raw TLV bytes
     * @return List of parsed TLV elements
     * @throws NfcEmvException.TlvParsingException if parsing fails
     */
    fun parse(data: ByteArray): List<TlvElement> {
        val elements = mutableListOf<TlvElement>()
        var offset = 0

        while (offset < data.size) {
            val (element, newOffset) = parseElement(data, offset)
            elements.add(element)
            offset = newOffset
        }

        return elements
    }

    /**
     * Parses a single TLV element starting at the given offset.
     *
     * @param data The source byte array
     * @param offset Starting position
     * @return Pair of (TlvElement, next offset)
     */
    private fun parseElement(data: ByteArray, offset: Int): Pair<TlvElement, Int> {
        if (offset >= data.size) {
            throw TapCardException.TlvParsingException("Unexpected end of data at offset $offset")
        }

        var currentOffset = offset

        // Parse tag
        val (tag, tagBytes) = parseTag(data, currentOffset)
        currentOffset += tagBytes

        // Parse length
        val (length, lengthBytes) = parseLength(data, currentOffset)
        currentOffset += lengthBytes

        // Validate we have enough data
        if (currentOffset + length > data.size) {
            throw TapCardException.TlvParsingException(
                "Length $length exceeds available data at offset $currentOffset"
            )
        }

        // Extract value
        val value = data.copyOfRange(currentOffset, currentOffset + length)

        // Extract raw TLV (tag + length + value)
        val rawEnd = currentOffset + length
        val raw = data.copyOfRange(offset, rawEnd)

        currentOffset += length

        return TlvElement(tag, length, value, raw) to currentOffset
    }

    /**
     * Parses a tag starting at the given offset.
     * Handles 1, 2, and 3-byte tags per EMV specification.
     *
     * @return Pair of (tag value, number of bytes consumed)
     */
    private fun parseTag(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= data.size) {
            throw TapCardException.TlvParsingException("Unexpected end of data parsing tag")
        }

        var tag = data[offset].toInt() and 0xFF
        var bytesRead = 1

        // Multi-byte tag: first byte has bits b5-b1 set to 1xxxx1
        if ((tag and 0x1F) == 0x1F) {
            if (offset + bytesRead >= data.size) {
                throw TapCardException.TlvParsingException("Incomplete multi-byte tag")
            }

            tag = (tag shl 8) or (data[offset + bytesRead].toInt() and 0xFF)
            bytesRead++

            // Continue if more tag bytes follow (bit 8 of the LAST byte read = 1)
            // We check the byte we just read, not the accumulated tag
            while ((data[offset + bytesRead - 1].toInt() and 0x80) != 0) {
                if (offset + bytesRead >= data.size) {
                    throw TapCardException.TlvParsingException("Incomplete multi-byte tag")
                }
                tag = (tag shl 8) or (data[offset + bytesRead].toInt() and 0xFF)
                bytesRead++
            }
        }

        return tag to bytesRead
    }

    /**
     * Parses a length field starting at the given offset.
     * Handles short form (1 byte) and long form (2-4 bytes) per EMV spec.
     *
     * @return Pair of (length value, number of bytes consumed)
     */
    private fun parseLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= data.size) {
            throw TapCardException.TlvParsingException("Unexpected end of data parsing length")
        }

        val firstByte = data[offset].toInt() and 0xFF

        // Short form: bit 8 = 0
        if ((firstByte and 0x80) == 0) {
            return firstByte to 1
        }

        // Long form: bit 8 = 1, bits 7-1 = number of additional bytes
        val numLengthBytes = firstByte and 0x7F

        if (numLengthBytes == 0) {
            // Indefinite length form (not commonly used in EMV)
            throw TapCardException.TlvParsingException("Indefinite length form not supported")
        }

        if (numLengthBytes > 3) {
            throw TapCardException.TlvParsingException("Length field too long: $numLengthBytes bytes")
        }

        if (offset + numLengthBytes >= data.size) {
            throw TapCardException.TlvParsingException("Incomplete length field")
        }

        var length = 0
        for (i in 1..numLengthBytes) {
            length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
        }

        return length to (numLengthBytes + 1)
    }

    /**
     * Finds a specific tag in a list of TLV elements.
     * Does not recursively search into constructed elements.
     *
     * @param elements List of TLV elements to search
     * @param tag The tag to find
     * @return The TlvElement if found, null otherwise
     */
    fun findTag(elements: List<TlvElement>, tag: Int): TlvElement? {
        return elements.find { it.tag == tag }
    }

    /**
     * Recursively searches for a tag in TLV elements and their children.
     *
     * @param elements List of TLV elements to search
     * @param tag The tag to find
     * @return The TlvElement if found, null otherwise
     */
    fun findTagRecursive(elements: List<TlvElement>, tag: Int): TlvElement? {
        for (element in elements) {
            if (element.tag == tag) {
                return element
            }
            if (element.isConstructed()) {
                val children = try {
                    element.parseChildren(this)
                } catch (e: TapCardException) {
                    emptyList()
                }
                val found = findTagRecursive(children, tag)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /**
     * Finds all occurrences of a tag recursively.
     *
     * @param elements List of TLV elements to search
     * @param tag The tag to find
     * @return List of matching TlvElements
     */
    fun findAllTags(elements: List<TlvElement>, tag: Int): List<TlvElement> {
        val results = mutableListOf<TlvElement>()
        findAllTagsRecursive(elements, tag, results)
        return results
    }

    private fun findAllTagsRecursive(elements: List<TlvElement>, tag: Int, results: MutableList<TlvElement>) {
        for (element in elements) {
            if (element.tag == tag) {
                results.add(element)
            }
            if (element.isConstructed()) {
                try {
                    val children = element.parseChildren(this)
                    findAllTagsRecursive(children, tag, results)
                } catch (e: TapCardException) {
                    // Ignore parsing errors in nested elements
                }
            }
        }
    }

    companion object {
        /**
         * Converts a byte array to a hex string for debugging.
         * PCI compliant - use only for non-sensitive data.
         */
        fun toHexString(data: ByteArray?): String {
            if (data == null) return "null"
            return data.joinToString("") { "%02X".format(it) }
        }

        /**
         * Parses a BCD-encoded date from bytes.
         * Expected format: YYMMDD (3 bytes)
         */
        fun parseBcdDate(data: ByteArray): String? {
            if (data.size < 3) return null
            val hex = toHexString(data)
            return if (hex.length >= 6) {
                // Format as YY/MM from YYMMDD
                hex.substring(2, 4) + "/" + hex.substring(0, 2)
            } else null
        }

        /**
         * Parses Track 2 equivalent data to extract PAN and expiry.
         * Track 2 format: PAN (up to 19 digits) + '=' + expiry (YYMM) + service code + discretionary data
         */
        fun parseTrack2Data(data: ByteArray): Pair<String?, String?> {
            val hex = toHexString(data)

            // Find the field separator (D in hex, representing '=')
            val separatorIndex = hex.indexOf('D')
            if (separatorIndex == -1) return null to null

            val pan = hex.substring(0, separatorIndex)

            // Expiry date follows separator: YYMM
            val expiry = if (hex.length > separatorIndex + 5) {
                val yy = hex.substring(separatorIndex + 1, separatorIndex + 3)
                val mm = hex.substring(separatorIndex + 3, separatorIndex + 5)
                "$mm/$yy"
            } else null

            return pan to expiry
        }
    }
}

/**
 * Extension function to parse byte array directly.
 */
fun ByteArray.parseTlv(): List<TlvParser.TlvElement> {
    return TlvParser().parse(this)
}

/**
 * Extension function to find a tag in parsed TLV data.
 */
fun List<TlvParser.TlvElement>.findTag(tag: Int): TlvParser.TlvElement? {
    return TlvParser().findTag(this, tag)
}

/**
 * Extension function to recursively find a tag.
 */
fun List<TlvParser.TlvElement>.findTagRecursive(tag: Int): TlvParser.TlvElement? {
    return TlvParser().findTagRecursive(this, tag)
}
