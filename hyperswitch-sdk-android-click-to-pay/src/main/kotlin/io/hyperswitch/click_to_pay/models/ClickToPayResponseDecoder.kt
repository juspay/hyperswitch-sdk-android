package io.hyperswitch.click_to_pay.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sealed class representing the result of a decode operation.
 */
sealed class DecodeResult<out T> {
    data class Success<T>(val data: T) : DecodeResult<T>()
    data class Error(val message: String, val field: String? = null) : DecodeResult<Nothing>()
}

/**
 * Decoder for Click to Pay API responses from WebView JavaScript bridge.
 *
 * This class centralizes all JSON parsing logic for Click to Pay responses,
 * providing type-safe decoding with proper error handling and validation.
 */
object ClickToPayResponseDecoder {

    /**
     * Parses a raw JSON string into a JSONObject.
     *
     * @param data The JSON string to parse
     * @return DecodeResult containing the JSONObject or error details
     */
    fun parseJSONObject(data: String): DecodeResult<JSONObject> {
        return try {
            DecodeResult.Success(JSONObject(data))
        } catch (e: Exception) {
            DecodeResult.Error(
                message = "Failed to parse JSON response: ${e.message}",
                field = "root"
            )
        }
    }

    /**
     * Safely extracts a nested JSONObject from a parent object.
     *
     * @param obj The parent JSONObject
     * @param name The key name for the nested object
     * @return The nested JSONObject, or empty JSONObject if not found or null
     */
    fun getNestedObject(obj: JSONObject, name: String): JSONObject {
        return obj.optJSONObject(name) ?: JSONObject()
    }

    /**
     * Safely extracts an item from a JSONArray as a JSONObject.
     *
     * @param array The source JSONArray
     * @param index The index to retrieve
     * @return The JSONObject at the index, or empty JSONObject if invalid
     */
    fun getArrayItem(array: JSONArray, index: Int): JSONObject {
        return array.optJSONObject(index) ?: JSONObject()
    }

    /**
     * Safely extracts a string value from a JSONObject, handling null values.
     *
     * @param obj The JSONObject to read from
     * @param key The key to look up
     * @return The string value if present and non-empty, null otherwise
     */
    fun extractString(obj: JSONObject, key: String): String? {
        return when {
            !obj.has(key) -> null
            obj.isNull(key) -> null
            else -> obj.optString(key).takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Extracts a required string value, returning an error if missing.
     *
     * @param obj The JSONObject to read from
     * @param key The key to look up
     * @return DecodeResult containing the string or error
     */
    fun extractRequiredString(obj: JSONObject, key: String): DecodeResult<String> {
        return when (val value = extractString(obj, key)) {
            null -> DecodeResult.Error(
                message = "Required field '$key' is missing or empty",
                field = key
            )
            else -> DecodeResult.Success(value)
        }
    }

    /**
     * Extracts an integer value, returning null for invalid or missing values.
     *
     * @param obj The JSONObject to read from
     * @param key The key to look up
     * @param minValue Optional minimum valid value (returns null if below)
     * @return The integer value if valid, null otherwise
     */
    fun extractInt(obj: JSONObject, key: String, minValue: Int? = null): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optInt(key, -1)
        if (value == -1 && !obj.isNull(key)) {
            // optInt returned default, but value exists - might not be a number
            return try {
                obj.getInt(key)
            } catch (_: Exception) {
                null
            }
        }
        return minValue?.let { if (value >= it) value else null } ?: value.takeIf { it >= 0 }
    }

    /**
     * Extracts a boolean value with a default fallback.
     *
     * @param obj The JSONObject to read from
     * @param key The key to look up
     * @param default The default value if missing
     * @return The boolean value or default
     */
    fun extractBoolean(obj: JSONObject, key: String, default: Boolean = false): Boolean {
        return if (obj.has(key) && !obj.isNull(key)) {
            obj.optBoolean(key, default)
        } else default
    }

    /**
     * Decodes error information from a response data object.
     *
     * @param data The data JSONObject that may contain an error
     * @return Pair of (errorType, errorMessage) if error exists, null otherwise
     */
    fun decodeError(data: JSONObject): Pair<String, String>? {
        val error = data.optJSONObject("error") ?: return null
        val errorType = error.optString("type", "Unknown")
        val errorMessage = error.optString("message", "Unknown error")
        return errorType to errorMessage
    }

    /**
     * Decodes a CustomerPresenceResponse from JSON data.
     *
     * @param data The JSONObject containing the response data
     * @return DecodeResult containing the parsed response
     */
    fun decodeCustomerPresenceResponse(data: JSONObject): DecodeResult<CustomerPresenceResponse> {
        val customerPresent = extractBoolean(data, "customerPresent", false)
        return DecodeResult.Success(CustomerPresenceResponse(customerPresent = customerPresent))
    }

    /**
     * Decodes masked validation channel information.
     *
     * @param obj The JSONObject containing the masked validation channel
     * @return MaskedValidationChannel with extracted data
     */
    fun decodeMaskedValidationChannel(obj: JSONObject?): MaskedValidationChannel? {
        if (obj == null || obj.length() == 0) return null
        return MaskedValidationChannel(
            email = extractString(obj, "email"),
            phoneNumber = extractString(obj, "phoneNumber")
        )
    }

    /**
     * Decodes a list of supported validation channels.
     *
     * @param array The JSONArray containing validation channels
     * @return List of SupportedValidationChannel objects
     */
    fun decodeSupportedValidationChannels(array: JSONArray?): List<SupportedValidationChannel> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = getArrayItem(array, index)
            if (obj.length() == 0) null else decodeSupportedValidationChannel(obj)
        }
    }

    /**
     * Decodes a single supported validation channel.
     *
     * @param obj The JSONObject containing the channel data
     * @return Decoded SupportedValidationChannel
     */
    fun decodeSupportedValidationChannel(obj: JSONObject): SupportedValidationChannel {
        return SupportedValidationChannel(
            validationChannelId = extractString(obj, "validationChannelId"),
            identityProvider = extractString(obj, "identityProvider"),
            identityType = extractString(obj, "identityType"),
            maskedValidationChannel = extractString(obj, "maskedValidationChannel")
        )
    }

    /**
     * Decodes a CardsStatusResponse from JSON data.
     *
     * @param data The JSONObject containing the response data
     * @return DecodeResult containing the parsed response
     */
    fun decodeCardsStatusResponse(data: JSONObject): DecodeResult<CardsStatusResponse> {
        val statusCodeStr = extractString(data, "statusCode") ?: "NO_CARDS_PRESENT"
        val maskedValidationChannel = decodeMaskedValidationChannel(
            data.optJSONObject("maskedValidationChannel")
        )
        val supportedValidationChannels = decodeSupportedValidationChannels(
            data.optJSONArray("supportedValidationChannels")
        )

        return DecodeResult.Success(
            CardsStatusResponse(
                statusCode = StatusCode.from(statusCodeStr),
                maskedValidationChannel = maskedValidationChannel,
                supportedValidationChannels = supportedValidationChannels.takeIf { it.isNotEmpty() }
            )
        )
    }

    /**
     * Decodes a list of recognized cards from a JSON array.
     *
     * @param data The JSONArray containing card data
     * @return DecodeResult containing the list of parsed cards
     */
    fun decodeRecognizedCards(data: JSONArray): DecodeResult<List<RecognizedCard>> {
        return try {
            val cards = (0 until data.length()).map { index ->
                decodeRecognizedCard(getArrayItem(data, index))
            }
            DecodeResult.Success(cards)
        } catch (e: Exception) {
            DecodeResult.Error(
                message = "Failed to parse recognized cards: ${e.message}",
                field = "cards"
            )
        }
    }

    /**
     * Decodes a single recognized card from JSON.
     *
     * @param obj The JSONObject containing card data
     * @return Decoded RecognizedCard
     */
    fun decodeRecognizedCard(obj: JSONObject): RecognizedCard {
        val digitalCardData = decodeDigitalCardData(obj.optJSONObject("digitalCardData"))
        val maskedBillingAddress = decodeMaskedBillingAddress(obj.optJSONObject("maskedBillingAddress"))
        val dcf = decodeDCF(obj.optJSONObject("dcf"))

        return RecognizedCard(
            srcDigitalCardId = extractRequiredString(obj, "srcDigitalCardId").let {
                when (it) {
                    is DecodeResult.Success -> it.data
                    is DecodeResult.Error -> ""
                }
            },
            panBin = extractString(obj, "panBin"),
            panLastFour = extractString(obj, "panLastFour"),
            panExpirationMonth = extractString(obj, "panExpirationMonth"),
            panExpirationYear = extractString(obj, "panExpirationYear"),
            tokenLastFour = extractString(obj, "tokenLastFour"),
            tokenBinRange = extractString(obj, "tokenBinRange"),
            digitalCardData = digitalCardData,
            countryCode = extractString(obj, "countryCode"),
            maskedBillingAddress = maskedBillingAddress,
            dateOfCardCreated = extractString(obj, "dateOfCardCreated"),
            dateOfCardLastUsed = extractString(obj, "dateOfCardLastUsed"),
            paymentAccountReference = extractString(obj, "paymentAccountReference"),
            paymentCardDescriptor = CardType.from(extractString(obj, "paymentCardDescriptor")),
            paymentCardType = extractString(obj, "paymentCardType"),
            dcf = dcf,
            digitalCardFeatures = obj.optJSONObject("digitalCardFeatures")?.let { emptyMap() }
        )
    }

    /**
     * Decodes digital card data from JSON.
     *
     * @param obj The JSONObject containing digital card data
     * @return Decoded DigitalCardData or null
     */
    fun decodeDigitalCardData(obj: JSONObject?): DigitalCardData? {
        if (obj == null) return null

        val authMethods = obj.optJSONArray("authenticationMethods")?.let { array ->
            (0 until array.length()).map { index ->
                val methodObj = getArrayItem(array, index)
                AuthenticationMethod(
                    authenticationMethodType = methodObj.optString("authenticationMethodType", "")
                )
            }
        }

        val pendingEvents = obj.optJSONArray("pendingEvents")?.let { array ->
            (0 until array.length()).map { index ->
                array.optString(index, "")
            }.filter { it.isNotEmpty() }
        }

        return DigitalCardData(
            status = extractString(obj, "status"),
            presentationName = extractString(obj, "presentationName"),
            descriptorName = obj.optString("descriptorName", "").takeIf { it.isNotEmpty() },
            artUri = extractString(obj, "artUri"),
            artHeight = extractInt(obj, "artHeight", minValue = 1),
            artWidth = extractInt(obj, "artWidth", minValue = 1),
            authenticationMethods = authMethods,
            pendingEvents = pendingEvents
        )
    }

    /**
     * Decodes masked billing address from JSON.
     *
     * @param obj The JSONObject containing address data
     * @return Decoded MaskedBillingAddress or null
     */
    fun decodeMaskedBillingAddress(obj: JSONObject?): MaskedBillingAddress? {
        if (obj == null || obj.length() == 0) return null
        return MaskedBillingAddress(
            addressId = extractString(obj, "addressId"),
            name = extractString(obj, "name"),
            line1 = extractString(obj, "line1"),
            line2 = extractString(obj, "line2"),
            line3 = extractString(obj, "line3"),
            city = extractString(obj, "city"),
            state = extractString(obj, "state"),
            countryCode = extractString(obj, "countryCode"),
            zip = extractString(obj, "zip")
        )
    }

    /**
     * Decodes DCF (Digital Card Facilitator) information.
     *
     * @param obj The JSONObject containing DCF data
     * @return Decoded DCF or null
     */
    fun decodeDCF(obj: JSONObject?): DCF? {
        if (obj == null) return null
        return DCF(
            name = extractString(obj, "name"),
            uri = extractString(obj, "uri"),
            logoUri = extractString(obj, "logoUri")
        )
    }

    /**
     * Decodes payment data (either card data or network token).
     *
     * @param obj The JSONObject containing payment data
     * @return Decoded PaymentData or null
     */
    fun decodePaymentData(obj: JSONObject?): PaymentData? {
        if (obj == null) return null

        val typeStr = obj.optString("type", "").uppercase()
        val tokenType = runCatching { DataType.valueOf(typeStr) }.getOrNull()

        return when (tokenType) {
            DataType.CARD_DATA -> PaymentData.CardData(
                cardNumber = extractString(obj, "cardNumber"),
                cardCvc = extractString(obj, "cardCvc"),
                cardExpiryMonth = extractString(obj, "cardExpiryMonth"),
                cardExpiryYear = extractString(obj, "cardExpiryYear")
            )

            DataType.NETWORK_TOKEN_DATA -> PaymentData.NetworkTokenData(
                networkToken = extractString(obj, "networkToken"),
                networkTokenCryptogram = extractString(obj, "networkTokenCryptogram"),
                networkTokenExpiryMonth = extractString(obj, "networkTokenExpiryMonth"),
                networkTokenExpiryYear = extractString(obj, "networkTokenExpiryYear")
            )

            else -> null
        }
    }

    /**
     * Decodes acquirer details from JSON.
     *
     * @param obj The JSONObject containing acquirer details
     * @return Decoded AcquirerDetails or null
     */
    fun decodeAcquirerDetails(obj: JSONObject?): AcquirerDetails? {
        if (obj == null) return null
        return AcquirerDetails(
            acquirerBin = extractString(obj, "acquirerBin"),
            acquirerMerchantId = extractString(obj, "acquirerMerchantId"),
            merchantCountryCode = extractString(obj, "merchantCountryCode")
        )
    }

    /**
     * Decodes a CheckoutResponse from JSON data.
     *
     * @param data The JSONObject containing checkout response data
     * @return DecodeResult containing the parsed response
     */
    fun decodeCheckoutResponse(data: JSONObject): DecodeResult<CheckoutResponse> {
        return try {
            val vaultTokenData = decodePaymentData(data.optJSONObject("vaultTokenData"))
            val paymentMethodData = decodePaymentData(data.optJSONObject("paymentMethodData"))
            val acquirerDetails = decodeAcquirerDetails(data.optJSONObject("acquirerDetails"))

            val statusStr = extractString(data, "status")?.uppercase() ?: ""
            val authStatus = runCatching { AuthenticationStatus.valueOf(statusStr) }.getOrNull()

            val response = CheckoutResponse(
                authenticationId = extractString(data, "authenticationId"),
                merchantId = extractString(data, "merchantId"),
                status = authStatus,
                clientSecret = extractString(data, "clientSecret"),
                amount = extractInt(data, "amount"),
                currency = extractString(data, "currency"),
                authenticationConnector = extractString(data, "authenticationConnector"),
                force3dsChallenge = extractBoolean(data, "force3dsChallenge", false),
                returnUrl = extractString(data, "returnUrl"),
                createdAt = extractString(data, "createdAt"),
                profileId = extractString(data, "profileId"),
                psd2ScaExemptionType = extractString(data, "psd2ScaExemptionType"),
                acquirerDetails = acquirerDetails,
                threedsServerTransactionId = extractString(data, "threeDsServerTransactionId"),
                maximumSupported3dsVersion = extractString(data, "maximumSupported3dsVersion"),
                connectorAuthenticationId = extractString(data, "connectorAuthenticationId"),
                threeDsMethodData = extractString(data, "threeDsMethod_data"),
                threeDsMethodUrl = extractString(data, "threeDsMethodUrl"),
                messageVersion = extractString(data, "messageVersion"),
                connectorMetadata = extractString(data, "connectorMetadata"),
                directoryServerId = extractString(data, "directoryServerId"),
                vaultTokenData = vaultTokenData,
                paymentMethodData = paymentMethodData,
                billing = extractString(data, "billing"),
                shipping = extractString(data, "shipping"),
                browserInformation = extractString(data, "browserInformation"),
                email = extractString(data, "email"),
                transStatus = extractString(data, "transStatus"),
                acsUrl = extractString(data, "acsUrl"),
                challengeRequest = extractString(data, "challengeRequest"),
                acsReferenceNumber = extractString(data, "acsReferenceNumber"),
                acsTransId = extractString(data, "acsTransId"),
                acsSignedContent = extractString(data, "acsSignedContent"),
                threeDsRequestorUrl = extractString(data, "threeDsRequestorUrl"),
                threeDsRequestorAppUrl = extractString(data, "threeDsRequestorAppUrl"),
                eci = extractString(data, "eci"),
                errorMessage = extractString(data, "errorMessage"),
                errorCode = extractString(data, "errorCode"),
                profileAcquirerId = extractString(data, "profileAcquirerId")
            )
            DecodeResult.Success(response)
        } catch (e: Exception) {
            DecodeResult.Error(
                message = "Failed to parse checkout response: ${e.message}",
                field = "checkout"
            )
        }
    }

    /**
     * Decodes a SignOutResponse from JSON data.
     *
     * @param data The JSONObject containing the response data
     * @return DecodeResult containing the parsed response
     */
    fun decodeSignOutResponse(data: JSONObject): DecodeResult<SignOutResponse> {
        val recognized = extractBoolean(data, "recognized", false)
        return DecodeResult.Success(SignOutResponse(recognized = recognized))
    }

    /**
     * Validates that a response contains a success flag without errors.
     *
     * @param data The JSONObject containing the response data
     * @return DecodeResult containing Unit on success, or error details
     */
    fun decodeSuccessResponse(data: JSONObject): DecodeResult<Unit> {
        decodeError(data)?.let { (type, message) ->
            return DecodeResult.Error(message = message, field = type)
        }
        return DecodeResult.Success(Unit)
    }
}
