package io.hyperswitch.click_to_pay.utils

import io.hyperswitch.click_to_pay.models.AcquirerDetails
import io.hyperswitch.click_to_pay.models.AuthenticationMethod
import io.hyperswitch.click_to_pay.models.AuthenticationStatus
import io.hyperswitch.click_to_pay.models.CardType
import io.hyperswitch.click_to_pay.models.CheckoutResponse
import io.hyperswitch.click_to_pay.models.ClickToPayException
import io.hyperswitch.click_to_pay.models.DCF
import io.hyperswitch.click_to_pay.models.DataType
import io.hyperswitch.click_to_pay.models.DigitalCardData
import io.hyperswitch.click_to_pay.models.MaskedBillingAddress
import io.hyperswitch.click_to_pay.models.MaskedValidationChannel
import io.hyperswitch.click_to_pay.models.PaymentData
import io.hyperswitch.click_to_pay.models.RecognizedCard
import io.hyperswitch.click_to_pay.models.SupportedValidationChannel
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogType
import org.json.JSONArray
import org.json.JSONObject

class ClickToPayModelParser {
    object Companion {
        private var logger: ((LogType, EventName, String, LogCategory) -> Unit)? = null

        fun setLogger(logger: (LogType, EventName, String, LogCategory) -> Unit) {
            this.logger = logger
        }

        fun parseJSONObject(data: String, eventName: EventName): JSONObject {
            try {
                return JSONObject(data)
            } catch (e: Exception) {
                logger?.invoke(
                    LogType.ERROR,
                    eventName,
                    "Type: ERROR, Message: Failed to parse JSONObject",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to read response: ${e.message}", "ERROR"
                )
            }
        }

        fun getOptJSONArray(arr: JSONArray, index: Int): JSONObject {
            return arr.optJSONObject(index) ?: JSONObject()
        }

        fun getOptJSONObject(obj: JSONObject, name: String): JSONObject {
            return obj.optJSONObject(name) ?: JSONObject()
        }

        fun safeReturnStringValue(
            obj: JSONObject, key: String
        ): String? {
            return when {
                obj.isNull(key) -> null
                else -> obj.getString(key).takeIf { it.isNotEmpty() }
            }
        }

        fun parseMaskedValidationChannelData(obj: JSONObject): MaskedValidationChannel {
            return MaskedValidationChannel(
                email = safeReturnStringValue(obj, "email"),
                phoneNumber = safeReturnStringValue(obj, "phoneNumber"),
            )
        }

        fun parseSupportedValidationChannelsData(obj: JSONObject): SupportedValidationChannel {
            return SupportedValidationChannel(
                validationChannelId = safeReturnStringValue(obj, "validationChannelId"),
                identityProvider = safeReturnStringValue(obj, "identityProvider"),
                identityType = safeReturnStringValue(obj, "identityType"),
                maskedValidationChannel = safeReturnStringValue(obj, "maskedValidationChannel")
            )
        }


        fun parseRecognizedCard(cardObj: JSONObject): RecognizedCard {
            val digitalCardDataObj = cardObj.optJSONObject("digitalCardData")
            val maskedBillingAddressObj = cardObj.optJSONObject("maskedBillingAddress")
            val dcfObj = cardObj.optJSONObject("dcf")

            val authMethods =
                digitalCardDataObj?.optJSONArray("authenticationMethods")?.let { arr ->
                    (0 until arr.length()).map { idx ->
                        AuthenticationMethod(
                            getOptJSONArray(arr, idx).optString("authenticationMethodType", "")
                        )
                    }
                }

            val pendingEvents = digitalCardDataObj?.optJSONArray("pendingEvents")?.let { arr ->
                (0 until arr.length()).map { idx -> arr.optString(idx, "") }
            }

            return RecognizedCard(
                srcDigitalCardId = safeReturnStringValue(cardObj, "srcDigitalCardId") ?: "",
                panBin = safeReturnStringValue(cardObj, "panBin"),
                panLastFour = safeReturnStringValue(cardObj, "panLastFour"),
                panExpirationMonth = safeReturnStringValue(cardObj, "panExpirationMonth"),
                panExpirationYear = safeReturnStringValue(cardObj, "panExpirationYear"),
                tokenLastFour = safeReturnStringValue(cardObj, "tokenLastFour"),
                tokenBinRange = safeReturnStringValue(cardObj, "tokenBinRange"),
                digitalCardData = digitalCardDataObj?.let {
                    DigitalCardData(
                        status = safeReturnStringValue(it, "status"),
                        presentationName = safeReturnStringValue(it, "presentationName"),
                        descriptorName = it.optString("descriptorName", ""),
                        artUri = safeReturnStringValue(it, "artUri"),
                        artHeight = it.optInt("artHeight", -1).takeIf { h -> h > 0 },
                        artWidth = it.optInt("artWidth", -1).takeIf { w -> w > 0 },
                        authenticationMethods = authMethods,
                        pendingEvents = pendingEvents
                    )
                },
                countryCode = safeReturnStringValue(cardObj, "countryCode"),
                maskedBillingAddress = maskedBillingAddressObj?.let { obj ->
                    if (obj.length() > 0) {
                        MaskedBillingAddress(
                            addressId = safeReturnStringValue(obj, "addressId"),
                            name = safeReturnStringValue(obj, "name"),
                            line1 = safeReturnStringValue(obj, "line1"),
                            line2 = safeReturnStringValue(obj, "line2"),
                            line3 = safeReturnStringValue(obj, "line3"),
                            city = safeReturnStringValue(obj, "city"),
                            state = safeReturnStringValue(obj, "state"),
                            countryCode = safeReturnStringValue(obj, "countryCode"),
                            zip = safeReturnStringValue(obj, "zip")
                        )
                    } else null
                },
                dateOfCardCreated = safeReturnStringValue(cardObj, "dateOfCardCreated"),
                dateOfCardLastUsed = safeReturnStringValue(cardObj, "dateOfCardLastUsed"),
                paymentAccountReference = safeReturnStringValue(cardObj, "paymentAccountReference"),
                paymentCardDescriptor = CardType.Companion.from(
                    cardObj.optString(
                        "paymentCardDescriptor", "unknown"
                    )
                ),
                paymentCardType = safeReturnStringValue(cardObj, "paymentCardType"),
                dcf = dcfObj?.let {
                    DCF(
                        name = safeReturnStringValue(it, "name"),
                        uri = safeReturnStringValue(it, "uri"),
                        logoUri = safeReturnStringValue(it, "logoUri")
                    )
                },
                digitalCardFeatures = cardObj.optJSONObject("digitalCardFeatures")
                    ?.let { emptyMap() })
        }

        fun parsePaymentData(obj: JSONObject?): PaymentData? {
            obj ?: return null
            val typeStr = obj.optString("type", "").uppercase()
            val tokenType = runCatching { DataType.valueOf(typeStr) }.getOrNull()

            return when (tokenType) {
                DataType.CARD_DATA -> PaymentData.CardData(
                    cardNumber = safeReturnStringValue(obj, "cardNumber"),
                    cardCvc = safeReturnStringValue(obj, "cardCvc"),
                    cardExpiryMonth = safeReturnStringValue(obj, "cardExpiryMonth"),
                    cardExpiryYear = safeReturnStringValue(obj, "cardExpiryYear"),
                )

                DataType.NETWORK_TOKEN_DATA -> PaymentData.NetworkTokenData(
                    networkToken = safeReturnStringValue(obj, "networkToken"),
                    networkTokenCryptogram = safeReturnStringValue(obj, "networkTokenCryptogram"),
                    networkTokenExpiryMonth = safeReturnStringValue(obj, "networkTokenExpiryMonth"),
                    networkTokenExpiryYear = safeReturnStringValue(obj, "networkTokenExpiryYear")
                )

                else -> null
            }
        }

        fun parseCheckoutResponse(data: JSONObject): CheckoutResponse {
            val vaultTokenDataObj = data.optJSONObject("vaultTokenData")
            val vaultTokenData = parsePaymentData(vaultTokenDataObj)
            val paymentMethodDataObj = data.optJSONObject("paymentMethodData")
            val paymentMethodData = parsePaymentData(paymentMethodDataObj)

            val acquirerDetailsObj = data.optJSONObject("acquirerDetails")
            val acquirerDetails = acquirerDetailsObj?.let {
                AcquirerDetails(
                    acquirerBin = safeReturnStringValue(it, "acquirerBin"),
                    acquirerMerchantId = safeReturnStringValue(it, "acquirerMerchantId"),
                    merchantCountryCode = safeReturnStringValue(it, "merchantCountryCode")
                )
            }

            val statusStr = data.optString("status", "").uppercase()
            val authStatus = try {
                AuthenticationStatus.valueOf(statusStr)
            } catch (_: IllegalArgumentException) {
                null
            }

            return CheckoutResponse(
                authenticationId = safeReturnStringValue(data, "authenticationId"),
                merchantId = safeReturnStringValue(data, "merchantId"),
                status = authStatus,
                clientSecret = safeReturnStringValue(data, "clientSecret"),
                amount = data.optInt("amount", -1).takeIf { it >= 0 },
                currency = safeReturnStringValue(data, "currency"),
                authenticationConnector = safeReturnStringValue(data, "authenticationConnector"),
                force3dsChallenge = data.optBoolean("force3dsChallenge", false),
                returnUrl = safeReturnStringValue(data, "returnUrl"),
                createdAt = safeReturnStringValue(data, "createdAt"),
                profileId = safeReturnStringValue(data, "profileId"),
                psd2ScaExemptionType = safeReturnStringValue(data, "psd2ScaExemptionType"),
                acquirerDetails = acquirerDetails,
                threedsServerTransactionId = safeReturnStringValue(
                    data, "threeDsServerTransactionId"
                ),
                maximumSupported3dsVersion = safeReturnStringValue(
                    data, "maximumSupported3dsVersion"
                ),
                connectorAuthenticationId = safeReturnStringValue(
                    data, "connectorAuthenticationId"
                ),
                threeDsMethodData = safeReturnStringValue(data, "threeDsMethod_data"),
                threeDsMethodUrl = safeReturnStringValue(data, "threeDsMethodUrl"),
                messageVersion = safeReturnStringValue(data, "messageVersion"),
                connectorMetadata = safeReturnStringValue(data, "connectorMetadata"),
                directoryServerId = safeReturnStringValue(data, "directoryServerId"),
                vaultTokenData = vaultTokenData,
                paymentMethodData = paymentMethodData,
                billing = safeReturnStringValue(data, "billing"),
                shipping = safeReturnStringValue(data, "shipping"),
                browserInformation = safeReturnStringValue(data, "browserInformation"),
                email = safeReturnStringValue(data, "email"),
                transStatus = safeReturnStringValue(data, "transStatus"),
                acsUrl = safeReturnStringValue(data, "acsUrl"),
                challengeRequest = safeReturnStringValue(data, "challengeRequest"),
                acsReferenceNumber = safeReturnStringValue(data, "acsReferenceNumber"),
                acsTransId = safeReturnStringValue(data, "acsTransId"),
                acsSignedContent = safeReturnStringValue(data, "acsSignedContent"),
                threeDsRequestorUrl = safeReturnStringValue(data, "threeDsRequestorUrl"),
                threeDsRequestorAppUrl = safeReturnStringValue(data, "threeDsRequestorAppUrl"),
                eci = safeReturnStringValue(data, "eci"),
                errorMessage = safeReturnStringValue(data, "errorMessage"),
                errorCode = safeReturnStringValue(data, "errorCode"),
                profileAcquirerId = safeReturnStringValue(data, "profileAcquirerId")
            )
        }
    }
}