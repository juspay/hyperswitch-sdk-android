package io.hyperswitch.click_to_pay.utils

import io.hyperswitch.click_to_pay.models.*
import org.json.JSONObject

/**
 * Centralized JSON parsing utility for Click to Pay responses.
 * Handles parsing of various Click to Pay data structures.
 */
object ClickToPayJsonParser {
    
    /**
     * Safely extracts a string value from a JSONObject, returning null for empty or null values.
     *
     * @param obj The JSONObject to extract from
     * @param key The key to look up
     * @return The string value or null if not present/empty
     */
    fun safeGetString(obj: JSONObject, key: String): String? {
        return when {
            obj.isNull(key) -> null
            else -> obj.getString(key).takeIf { it.isNotEmpty() }
        }
    }
    
    /**
     * Parses a RecognizedCard from JSON response.
     *
     * @param cardObj The JSON object containing card data
     * @return Parsed RecognizedCard object
     */
    fun parseRecognizedCard(cardObj: JSONObject): RecognizedCard {
        val digitalCardDataObj = cardObj.optJSONObject("digitalCardData")
        val maskedBillingAddressObj = cardObj.optJSONObject("maskedBillingAddress")
        val dcfObj = cardObj.optJSONObject("dcf")

        val authMethods = digitalCardDataObj?.optJSONArray("authenticationMethods")?.let { arr ->
            (0 until arr.length()).map { idx ->
                AuthenticationMethod(
                    arr.getJSONObject(idx).optString("authenticationMethodType", "")
                )
            }
        }

        val pendingEvents = digitalCardDataObj?.optJSONArray("pendingEvents")?.let { arr ->
            (0 until arr.length()).map { idx -> arr.optString(idx, "") }
        }

        return RecognizedCard(
            srcDigitalCardId = safeGetString(cardObj, "srcDigitalCardId") ?: "",
            panBin = safeGetString(cardObj, "panBin"),
            panLastFour = safeGetString(cardObj, "panLastFour"),
            panExpirationMonth = safeGetString(cardObj, "panExpirationMonth"),
            panExpirationYear = safeGetString(cardObj, "panExpirationYear"),
            tokenLastFour = safeGetString(cardObj, "tokenLastFour"),
            tokenBinRange = safeGetString(cardObj, "tokenBinRange"),
            digitalCardData = digitalCardDataObj?.let {
                DigitalCardData(
                    status = safeGetString(it, "status"),
                    presentationName = safeGetString(it, "presentationName"),
                    descriptorName = it.optString("descriptorName", ""),
                    artUri = safeGetString(it, "artUri"),
                    artHeight = it.optInt("artHeight", -1).takeIf { h -> h > 0 },
                    artWidth = it.optInt("artWidth", -1).takeIf { w -> w > 0 },
                    authenticationMethods = authMethods,
                    pendingEvents = pendingEvents
                )
            },
            countryCode = safeGetString(cardObj, "countryCode"),
            maskedBillingAddress = maskedBillingAddressObj?.let { obj ->
                if (obj.length() > 0) {
                    MaskedBillingAddress(
                        addressId = safeGetString(obj, "addressId"),
                        name = safeGetString(obj, "name"),
                        line1 = safeGetString(obj, "line1"),
                        line2 = safeGetString(obj, "line2"),
                        line3 = safeGetString(obj, "line3"),
                        city = safeGetString(obj, "city"),
                        state = safeGetString(obj, "state"),
                        countryCode = safeGetString(obj, "countryCode"),
                        zip = safeGetString(obj, "zip")
                    )
                } else null
            },
            dateOfCardCreated = safeGetString(cardObj, "dateOfCardCreated"),
            dateOfCardLastUsed = safeGetString(cardObj, "dateOfCardLastUsed"),
            paymentAccountReference = safeGetString(cardObj, "paymentAccountReference"),
            paymentCardDescriptor = CardType.from(
                cardObj.optString("paymentCardDescriptor", "unknown")
            ),
            paymentCardType = safeGetString(cardObj, "paymentCardType"),
            dcf = dcfObj?.let {
                DCF(
                    name = safeGetString(it, "name"),
                    uri = safeGetString(it, "uri"),
                    logoUri = safeGetString(it, "logoUri")
                )
            },
            digitalCardFeatures = cardObj.optJSONObject("digitalCardFeatures")?.let { emptyMap() }
        )
    }
    
    /**
     * Parses PaymentData from JSON response.
     *
     * @param obj The JSON object containing payment data
     * @return Parsed PaymentData object or null if type is unknown
     */
    fun parsePaymentData(obj: JSONObject?): PaymentData? {
        obj ?: return null
        val typeStr = obj.optString("type", "").uppercase()
        val tokenType = runCatching { DataType.valueOf(typeStr) }.getOrNull()

        return when (tokenType) {
            DataType.CARD_DATA -> PaymentData.CardData(
                cardNumber = safeGetString(obj, "cardNumber"),
                cardCvc = safeGetString(obj, "cardCvc"),
                cardExpiryMonth = safeGetString(obj, "cardExpiryMonth"),
                cardExpiryYear = safeGetString(obj, "cardExpiryYear"),
            )

            DataType.NETWORK_TOKEN_DATA -> PaymentData.NetworkTokenData(
                networkToken = safeGetString(obj, "networkToken"),
                networkTokenCryptogram = safeGetString(obj, "networkTokenCryptogram"),
                networkTokenExpiryMonth = safeGetString(obj, "networkTokenExpiryMonth"),
                networkTokenExpiryYear = safeGetString(obj, "networkTokenExpiryYear")
            )

            else -> null
        }
    }
    
    /**
     * Parses a complete CheckoutResponse from JSON.
     *
     * @param data The JSON object containing checkout response data
     * @return Parsed CheckoutResponse object
     */
    fun parseCheckoutResponse(data: JSONObject): CheckoutResponse {
        val vaultTokenDataObj = data.optJSONObject("vaultTokenData")
        val vaultTokenData = parsePaymentData(vaultTokenDataObj)
        val paymentMethodDataObj = data.optJSONObject("paymentMethodData")
        val paymentMethodData = parsePaymentData(paymentMethodDataObj)

        val acquirerDetailsObj = data.optJSONObject("acquirerDetails")
        val acquirerDetails = acquirerDetailsObj?.let {
            AcquirerDetails(
                acquirerBin = safeGetString(it, "acquirerBin"),
                acquirerMerchantId = safeGetString(it, "acquirerMerchantId"),
                merchantCountryCode = safeGetString(it, "merchantCountryCode")
            )
        }

        val statusStr = data.optString("status", "").uppercase()
        val authStatus = try {
            AuthenticationStatus.valueOf(statusStr)
        } catch (_: IllegalArgumentException) {
            null
        }

        return CheckoutResponse(
            authenticationId = safeGetString(data, "authenticationId"),
            merchantId = safeGetString(data, "merchantId"),
            status = authStatus,
            clientSecret = safeGetString(data, "clientSecret"),
            amount = data.optInt("amount", -1).takeIf { it >= 0 },
            currency = safeGetString(data, "currency"),
            authenticationConnector = safeGetString(data, "authenticationConnector"),
            force3dsChallenge = data.optBoolean("force3dsChallenge", false),
            returnUrl = safeGetString(data, "returnUrl"),
            createdAt = safeGetString(data, "createdAt"),
            profileId = safeGetString(data, "profileId"),
            psd2ScaExemptionType = safeGetString(data, "psd2ScaExemptionType"),
            acquirerDetails = acquirerDetails,
            threedsServerTransactionId = safeGetString(data, "threeDsServerTransactionId"),
            maximumSupported3dsVersion = safeGetString(data, "maximumSupported3dsVersion"),
            connectorAuthenticationId = safeGetString(data, "connectorAuthenticationId"),
            threeDsMethodData = safeGetString(data, "threeDsMethod_data"),
            threeDsMethodUrl = safeGetString(data, "threeDsMethodUrl"),
            messageVersion = safeGetString(data, "messageVersion"),
            connectorMetadata = safeGetString(data, "connectorMetadata"),
            directoryServerId = safeGetString(data, "directoryServerId"),
            vaultTokenData = vaultTokenData,
            paymentMethodData = paymentMethodData,
            billing = safeGetString(data, "billing"),
            shipping = safeGetString(data, "shipping"),
            browserInformation = safeGetString(data, "browserInformation"),
            email = safeGetString(data, "email"),
            transStatus = safeGetString(data, "transStatus"),
            acsUrl = safeGetString(data, "acsUrl"),
            challengeRequest = safeGetString(data, "challengeRequest"),
            acsReferenceNumber = safeGetString(data, "acsReferenceNumber"),
            acsTransId = safeGetString(data, "acsTransId"),
            acsSignedContent = safeGetString(data, "acsSignedContent"),
            threeDsRequestorUrl = safeGetString(data, "threeDsRequestorUrl"),
            threeDsRequestorAppUrl = safeGetString(data, "threeDsRequestorAppUrl"),
            eci = safeGetString(data, "eci"),
            errorMessage = safeGetString(data, "errorMessage"),
            errorCode = safeGetString(data, "errorCode"),
            profileAcquirerId = safeGetString(data, "profileAcquirerId")
        )
    }
}
