package io.hyperswitch.click_to_pay

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.webview.utils.Arguments
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Default implementation of ClickToPaySessionLauncher.
 *
 * Manages Click to Pay session lifecycle using WebView-based JavaScript bridge.
 * Handles SDK initialization, customer verification, card management, and payment processing.
 *
 * @property activity The Android activity context
 * @property publishableKey The publishable API key for authentication
 * @property customBackendUrl Optional custom backend URL for API calls
 * @property customLogUrl Optional custom URL for logging
 * @property customParams Optional additional parameters
 * @property hSWebViewManagerImpl WebView manager for JavaScript execution
 * @property hSWebViewWrapper Wrapper for the WebView instance
 * @property pendingRequests Map of pending async requests awaiting responses
 */
class DefaultClickToPaySessionLauncher(
    private val activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
    private val customParams: Bundle? = null,
) : ClickToPaySessionLauncher {
    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper

    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()

    @Volatile
    private var isWebViewInitialized = false

    /**
     * Helper function to execute JavaScript on the Main thread and return the response.
     * This ensures WebView operations happen on the correct thread while keeping
     * JSON parsing and data transformation on background threads.
     *
     * @param requestId Unique identifier for tracking this request
     * @param jsCode The JavaScript code to execute
     * @return The JSON response string from the WebView
     */
    private suspend fun evaluateJavascriptOnMainThread(requestId: String, jsCode: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestId] = continuation
                hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
            }
        }
    }
    
    private fun parseRecognizedCard(cardObj: JSONObject): RecognizedCard {
        val digitalCardDataObj = cardObj.optJSONObject("digitalCardData")
        val maskedBillingAddressObj = cardObj.optJSONObject("maskedBillingAddress")
        val dcfObj = cardObj.optJSONObject("dcf")
        
        val authMethods = digitalCardDataObj?.optJSONArray("authenticationMethods")?.let { arr ->
            (0 until arr.length()).map { idx ->
                AuthenticationMethod(arr.getJSONObject(idx).optString("authenticationMethodType", ""))
            }
        }
        
        val pendingEvents = digitalCardDataObj?.optJSONArray("pendingEvents")?.let { arr ->
            (0 until arr.length()).map { idx -> arr.optString(idx, "") }
        }
        
        return RecognizedCard(
            srcDigitalCardId = cardObj.optString("srcDigitalCardId", ""),
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
            paymentCardDescriptor = CardType.valueOf(cardObj.optString("paymentCardDescriptor", "unknown").uppercase()),
            paymentCardType = safeReturnStringValue(cardObj, "paymentCardType"),
            dcf = dcfObj?.let {
                DCF(
                    name = safeReturnStringValue(it, "name"),
                    uri = safeReturnStringValue(it, "uri"),
                    logoUri = safeReturnStringValue(it, "logoUri")
                )
            },
            digitalCardFeatures = cardObj.optJSONObject("digitalCardFeatures")?.let { emptyMap() }
        )
    }

    /**
     * Initializes the WebView components asynchronously on the main thread.
     * This method is idempotent and can be called multiple times safely.
     *
     * @throws Exception if WebView initialization fails
     */
    private suspend fun ensureWebViewInitialized() {
        if (isWebViewInitialized) return

        withContext(Dispatchers.Main) {
            if (isWebViewInitialized) return@withContext

            val onMessage = Callback { args ->
                println(args)
                (args["data"] as? String)?.let { jsonString ->
                    val jsonObject = JSONObject(jsonString)
                    val requestId = jsonObject.optString("requestId", "")
                    if (requestId.isNotEmpty()) {
                        pendingRequests.remove(requestId)?.resume(jsonString)
                    }
                }
            }

            hSWebViewManagerImpl = HSWebViewManagerImpl(activity, onMessage)
            hSWebViewWrapper = hSWebViewManagerImpl.createViewInstance()

            hSWebViewManagerImpl.setJavaScriptEnabled(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setMessagingEnabled(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setJavaScriptCanOpenWindowsAutomatically(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setScalesPageToFit(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setMixedContentMode(hSWebViewWrapper, "compatibility")
            hSWebViewManagerImpl.setThirdPartyCookiesEnabled(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setCacheEnabled(hSWebViewWrapper, true)

            hSWebViewWrapper.layoutParams = LayoutParams(1, 1)

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(hSWebViewWrapper)

            isWebViewInitialized = true
        }
    }

    /**
     * Initializes the Click to Pay SDK by loading the HyperLoader script.
     *
     * Creates an HTML page with the HyperLoader.js script and initializes
     * the Hyper instance with the provided configuration.
     *
     * @throws Exception if SDK initialization fails with error details
     */
    @Throws(Exception::class)
    override suspend fun initialize() {
        ensureWebViewInitialized()
        loadUrl()
    }

    /**
     * Loads the SDK initialization HTML into the WebView.
     *
     * Creates and loads an HTML page containing the HyperLoader.js script
     * and initialization code. Waits for the SDK to initialize successfully.
     *
     * @param requestId Unique identifier for tracking this request
     * @throws Exception if script loading or initialization fails
     */
    private suspend fun loadUrl(requestId: String = UUID.randomUUID().toString()) {
        val baseHtml =
            "<!DOCTYPE html><html><head><script>function handleScriptError(){console.error('ClickToPay','Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='https://beta.hyperswitch.io/web/2025.11.20.00-c2p-headless/v2/HyperLoader.js' onload='initHyper()' onerror='handleScriptError()' async></script></head><body></body></html>".trimMargin()

        val responseJson = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestId] = continuation

                val map = Arguments.createMap()
                map.putString("html", baseHtml)
                map.putString("baseUrl", "https://secure.checkout.visa.com")
                hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
            }
        }

        withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw Exception("Failed to load URL - Type: $errorType, Message: $errorMessage")
            }
        }
    }

    /**
     * Initializes a Click to Pay session with payment credentials.
     *
     * Creates an authentication session and initializes Click to Pay
     * with the provided merchant and payment information.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @throws Exception if session initialization fails
     */
    @Throws(Exception::class)
    override suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ) {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const authenticationSession=window.hyperInstance.initAuthenticationSession({clientSecret:'$clientSecret',profileId:'$profileId',authenticationId:'$authenticationId',merchantId:'$merchantId'});window.ClickToPaySession=await authenticationSession.initClickToPaySession({request3DSAuthentication:$request3DSAuthentication});const data=window.ClickToPaySession.error?window.ClickToPaySession:{success:true};window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:data}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'InitClickToPaySessionError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw Exception("ClickToPay: Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage")
            }
        }
    }

    /**
     * Checks if a customer has an existing Click to Pay profile.
     *
     * Queries the Click to Pay service to determine if the customer
     * is enrolled based on their email or mobile number.
     *
     * @param request Customer identification details
     * @return CustomerPresenceResponse indicating enrollment status
     * @throws Exception if the check fails
     */
    @Throws(Exception::class)
    override suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const isCustomerPresent=await window.ClickToPaySession.isCustomerPresent({${request.email?.let { "email:'${request.email}'" } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:isCustomerPresent}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'IsCustomerPresentError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw Exception("Failed to check customer presence - Type: $errorType, Message: $errorMessage")
            }

            CustomerPresenceResponse(
                customerPresent = data.optBoolean("customerPresent", false)
            )
        }
    }

    /**
     * Retrieves the status of customer's saved cards.
     *
     * Determines whether the customer has recognized cards available
     * or if additional authentication (OTP) is required.
     *
     * @return CardsStatusResponse with status code
     * @throws Exception if retrieval fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun getUserType(): CardsStatusResponse {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const userType=await window.ClickToPaySession.getUserType();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:userType}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val typeString = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                
                val errorType = try {
                    ClickToPayErrorType.valueOf(typeString)
                } catch (e: IllegalArgumentException) {
                    ClickToPayErrorType.ERROR
                }

                throw ClickToPayException(
                    message = errorMessage,
                    type = errorType
                )
            }

            val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT").uppercase()
            CardsStatusResponse(
                statusCode = StatusCode.valueOf(statusCodeStr)
            )
        }
    }

    /**
     * Gets the list of recognized cards for the customer.
     *
     * Retrieves all cards associated with the customer's Click to Pay profile.
     * Parses card details including digital card data and billing address.
     *
     * @return List of RecognizedCard objects with complete card information
     * @throws Exception if card retrieval fails
     */
    @Throws(Exception::class)
    override suspend fun getRecognizedCards(): List<RecognizedCard> {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.getRecognizedCards();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'GetRecognizedCardsError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = data.getJSONObject("error")
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw Exception("Failed to get recognized cards - Type: $errorType, Message: $errorMessage")
            }

            val cardsArray = data as org.json.JSONArray
            (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(cardsArray.getJSONObject(i))
            }
        }
    }

    /**
     * Validates customer authentication with OTP.
     *
     * Verifies the OTP entered by the customer and retrieves their
     * recognized cards upon successful validation.
     *
     * @param otpValue The OTP value entered by the customer
     * @return List of RecognizedCard objects if validation successful
     * @throws Exception if OTP validation fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard> {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.validateCustomerAuthentication({value:'$otpValue'});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = data.getJSONObject("error")
                val typeString = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                
                val errorType = try {
                    ClickToPayErrorType.valueOf(typeString)
                } catch (_: IllegalArgumentException) {
                    ClickToPayErrorType.ERROR
                }

                throw ClickToPayException(
                    message = errorMessage,
                    type = errorType
                )
            }

            val cardsArray = data as org.json.JSONArray
            (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(cardsArray.getJSONObject(i))
            }
        }
    }

    private fun safeReturnStringValue(
        obj: JSONObject,
        key: String,
        fallback: String = ""
    ): String? {
        return obj.optString(key, fallback).takeIf { it.isNotEmpty() }
    }

    /**
     * Processes checkout with a selected card.
     *
     * Initiates payment processing using the customer's selected Click to Pay card.
     * Parses the complete checkout response including transaction details,
     * token data, and 3DS information.
     *
     * @param request CheckoutRequest containing card ID and preferences
     * @return CheckoutResponse with complete transaction details
     * @throws Exception if checkout fails
     */
    @Throws(Exception::class)
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const checkoutResponse=await window.ClickToPaySession.checkoutWithCard({srcDigitalCardId:'${request.srcDigitalCardId}',rememberMe:${request.rememberMe}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:checkoutResponse}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'CheckoutWithCardError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw Exception("Failed to checkout with card - Type: $errorType, Message: $errorMessage")
            }

            val vaultTokenDataObj = data.optJSONObject("vault_token_data")
            val vaultTokenData = vaultTokenDataObj?.let { vtd ->
                val typeStr = vtd.optString("type", "").uppercase()
                val tokenType = try {
                    VaultTokenType.valueOf(typeStr)
                } catch (e: IllegalArgumentException) {
                    null
                }

                VaultTokenData(
                    type = tokenType,
                    cardNumber = safeReturnStringValue(vtd, "card_number"),
                    cardCvc = safeReturnStringValue(vtd, "card_cvc"),
                    cardExpiryMonth = safeReturnStringValue(vtd, "card_expiry_month"),
                    cardExpiryYear = safeReturnStringValue(vtd, "card_expiry_year"),
                    paymentToken = safeReturnStringValue(vtd, "payment_token"),
                    tokenCryptogram = safeReturnStringValue(vtd, "token_cryptogram"),
                    tokenExpirationMonth = safeReturnStringValue(vtd, "token_expiration_month"),
                    tokenExpirationYear = safeReturnStringValue(vtd, "token_expiration_year")
                )
            }
            val paymentMethodDataObj = data.optJSONObject("payment_method_data")
            val paymentMethodData = paymentMethodDataObj?.let { vtd ->
                val typeStr = vtd.optString("type", "").uppercase()
                val tokenType = try {
                    PaymentMethodType.valueOf(typeStr)
                } catch (e: IllegalArgumentException) {
                    null
                }

                PaymentMethodData(
                    type = tokenType,
                    cardNumber = safeReturnStringValue(vtd, "card_number"),
                    cardCvc = safeReturnStringValue(vtd, "card_cvc"),
                    cardExpiryMonth = safeReturnStringValue(vtd, "card_expiry_month"),
                    cardExpiryYear = safeReturnStringValue(vtd, "card_expiry_year"),
                    paymentToken = safeReturnStringValue(vtd, "payment_token"),
                    tokenCryptogram = safeReturnStringValue(vtd, "token_cryptogram"),
                    tokenExpirationMonth = safeReturnStringValue(vtd, "token_expiration_month"),
                    tokenExpirationYear = safeReturnStringValue(vtd, "token_expiration_year")
                )
            }

            val acquirerDetailsObj = data.optJSONObject("acquirer_details")
            val acquirerDetails = acquirerDetailsObj?.let { it ->
                AcquirerDetails(
                    acquirerBin = safeReturnStringValue(it, "acquirer_bin"),
                    acquirerMerchantId = safeReturnStringValue(it, "acquirer_merchant_id"),
                    merchantCountryCode = safeReturnStringValue(it, "merchant_country_code")
                )
            }

            val statusStr = data.optString("status", "").uppercase()
            val authStatus = try {
                AuthenticationStatus.valueOf(statusStr)
            } catch (e: IllegalArgumentException) {
                null
            }

            CheckoutResponse(
                authenticationId = safeReturnStringValue(data, "authentication_id"),
                merchantId = safeReturnStringValue(data, "merchant_id"),
                status = authStatus,
                clientSecret = safeReturnStringValue(data, "client_secret"),
                amount = data.optInt("amount", -1).takeIf { it >= 0 },
                currency = safeReturnStringValue(data, "currency"),
                authenticationConnector = safeReturnStringValue(
                    data,
                    "authentication_connector",
                ),
                force3dsChallenge = data.optBoolean("force_3ds_challenge", false),
                returnUrl = safeReturnStringValue(data, "return_url"),
                createdAt = safeReturnStringValue(data, "created_at"),
                profileId = safeReturnStringValue(data, "profile_id"),
                psd2ScaExemptionType = safeReturnStringValue(data, "psd2_sca_exemption_type"),
                acquirerDetails = acquirerDetails,
                threedsServerTransactionId = safeReturnStringValue(
                    data,
                    "threeds_server_transaction_id",
                ),
                maximumSupported3dsVersion = safeReturnStringValue(
                    data,
                    "maximum_supported_3ds_version",
                ),
                connectorAuthenticationId = safeReturnStringValue(
                    data,
                    "connector_authentication_id"
                ),
                threeDsMethodData = safeReturnStringValue(data, "three_ds_method_data"),
                threeDsMethodUrl = safeReturnStringValue(data, "three_ds_method_url"),
                messageVersion = safeReturnStringValue(data, "message_version"),
                connectorMetadata = safeReturnStringValue(data, "connector_metadata"),
                directoryServerId = safeReturnStringValue(data, "directory_server_id"),
                vaultTokenData = vaultTokenData,
                paymentMethodData = paymentMethodData,
                billing = safeReturnStringValue(data, "billing"),
                shipping = safeReturnStringValue(data, "shipping"),
                browserInformation = safeReturnStringValue(data, "browser_information"),
                email = safeReturnStringValue(data, "email"),
                transStatus = safeReturnStringValue(data, "trans_status"),
                acsUrl = safeReturnStringValue(data, "acs_url"),
                challengeRequest = safeReturnStringValue(data, "challenge_request"),
                acsReferenceNumber = safeReturnStringValue(data, "acs_reference_number"),
                acsTransId = safeReturnStringValue(data, "acs_trans_id"),
                acsSignedContent = safeReturnStringValue(data, "acs_signed_content"),
                threeDsRequestorUrl = safeReturnStringValue(data, "three_ds_requestor_url"),
                threeDsRequestorAppUrl = safeReturnStringValue(
                    data,
                    "three_ds_requestor_app_url",
                    ),
                eci = safeReturnStringValue(data, "eci"),
                errorMessage = safeReturnStringValue(data, "error_message"),
                errorCode = safeReturnStringValue(data, "error_code"),
                profileAcquirerId = safeReturnStringValue(data, "profile_acquirer_id")
            )
        }
    }

    override suspend fun signOut(): SignOutResponse {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()
        val jsCode = "(async function(){try{const signOutResponse = await window.ClickToPaySession.signOut();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data: signOutResponse }));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{ error:{type:'SignOutError',message:error.message}}}))}})();".trimMargin()
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.optJSONObject("data")
            val error = jsonObject.optJSONObject("error")
            if (error != null){
                throw Exception("ClickToPay: Failed to SignOut because SignOutError : ${error.optString("message", "Error")} ")
            }
            SignOutResponse(
                recognized = data?.optBoolean("recognized", false)
            )
        }
    }
}
