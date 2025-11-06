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
            panBin = cardObj.optString("panBin", "").takeIf { it.isNotEmpty() },
            panLastFour = cardObj.optString("panLastFour", "").takeIf { it.isNotEmpty() },
            panExpirationMonth = cardObj.optString("panExpirationMonth", "").takeIf { it.isNotEmpty() },
            panExpirationYear = cardObj.optString("panExpirationYear", "").takeIf { it.isNotEmpty() },
            tokenLastFour = cardObj.optString("tokenLastFour", "").takeIf { it.isNotEmpty() },
            tokenBinRange = cardObj.optString("tokenBinRange", "").takeIf { it.isNotEmpty() },
            digitalCardData = digitalCardDataObj?.let {
                DigitalCardData(
                    status = it.optString("status", "").takeIf { s -> s.isNotEmpty() },
                    presentationName = it.optString("presentationName", "").takeIf { s -> s.isNotEmpty() },
                    descriptorName = it.optString("descriptorName", "").takeIf { s -> s.isNotEmpty() },
                    artUri = it.optString("artUri", "").takeIf { s -> s.isNotEmpty() },
                    artHeight = it.optInt("artHeight", -1).takeIf { h -> h > 0 },
                    artWidth = it.optInt("artWidth", -1).takeIf { w -> w > 0 },
                    authenticationMethods = authMethods,
                    pendingEvents = pendingEvents
                )
            },
            countryCode = cardObj.optString("countryCode", "").takeIf { it.isNotEmpty() },
            maskedBillingAddress = maskedBillingAddressObj?.let { obj ->
                if (obj.length() > 0) {
                    MaskedBillingAddress(
                        addressId = obj.optString("addressId", "").takeIf { it.isNotEmpty() },
                        name = obj.optString("name", "").takeIf { it.isNotEmpty() },
                        line1 = obj.optString("line1", "").takeIf { it.isNotEmpty() },
                        line2 = obj.optString("line2", "").takeIf { it.isNotEmpty() },
                        line3 = obj.optString("line3", "").takeIf { it.isNotEmpty() },
                        city = obj.optString("city", "").takeIf { it.isNotEmpty() },
                        state = obj.optString("state", "").takeIf { it.isNotEmpty() },
                        countryCode = obj.optString("countryCode", "").takeIf { it.isNotEmpty() },
                        zip = obj.optString("zip", "").takeIf { it.isNotEmpty() }
                    )
                } else null
            },
            dateOfCardCreated = cardObj.optString("dateOfCardCreated", "").takeIf { it.isNotEmpty() },
            dateOfCardLastUsed = cardObj.optString("dateOfCardLastUsed", "").takeIf { it.isNotEmpty() },
            paymentAccountReference = cardObj.optString("paymentAccountReference", "").takeIf { it.isNotEmpty() },
            paymentCardDescriptor = cardObj.optString("paymentCardDescriptor", "").takeIf { it.isNotEmpty() },
            paymentCardType = cardObj.optString("paymentCardType", "").takeIf { it.isNotEmpty() },
            dcf = dcfObj?.let {
                DCF(
                    name = it.optString("name", "").takeIf { s -> s.isNotEmpty() },
                    uri = it.optString("uri", "").takeIf { s -> s.isNotEmpty() },
                    logoUri = it.optString("logoUri", "").takeIf { s -> s.isNotEmpty() }
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
            "<!DOCTYPE html><html><head><script>function handleScriptError(){console.error('Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='https://beta.hyperswitch.io/v2/HyperLoader.js' onload='initHyper()' onerror='handleScriptError()' async></script></head><body></body></html>"

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
                throw Exception("Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage")
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
            "(async function(){try{const userType=await window.ClickToPaySession.getUserType();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:userType}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message,code:error.code}}}))}})();"

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

            val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT")
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
            "(async function(){try{const cards=await window.ClickToPaySession.validateCustomerAuthentication({value:'$otpValue'});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message,code:error.code}}}))}})();"

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
                    cardNumber = vtd.optString("card_number", "").takeIf { it.isNotEmpty() },
                    cardCvc = vtd.optString("card_cvc", "").takeIf { it.isNotEmpty() },
                    cardExpiryMonth = vtd.optString("card_expiry_month", "")
                        .takeIf { it.isNotEmpty() },
                    cardExpiryYear = vtd.optString("card_expiry_year", "")
                        .takeIf { it.isNotEmpty() },
                    paymentToken = vtd.optString("payment_token", "").takeIf { it.isNotEmpty() },
                    tokenCryptogram = vtd.optString("token_cryptogram", "")
                        .takeIf { it.isNotEmpty() },
                    tokenExpirationMonth = vtd.optString("token_expiration_month", "")
                        .takeIf { it.isNotEmpty() },
                    tokenExpirationYear = vtd.optString("token_expiration_year", "")
                        .takeIf { it.isNotEmpty() }
                )
            }

            val acquirerDetailsObj = data.optJSONObject("acquirer_details")
            val acquirerDetails = acquirerDetailsObj?.let {
                AcquirerDetails(
                    acquirerBin = it.optString("acquirer_bin", "").takeIf { s -> s.isNotEmpty() },
                    acquirerMerchantId = it.optString("acquirer_merchant_id", "")
                        .takeIf { s -> s.isNotEmpty() },
                    merchantCountryCode = it.optString("merchant_country_code", "")
                        .takeIf { s -> s.isNotEmpty() }
                )
            }

            val statusStr = data.optString("status", "").uppercase()
            val authStatus = try {
                AuthenticationStatus.valueOf(statusStr)
            } catch (e: IllegalArgumentException) {
                null
            }

            CheckoutResponse(
                authenticationId = data.optString("authentication_id", "")
                    .takeIf { it.isNotEmpty() },
                merchantId = data.optString("merchant_id", "").takeIf { it.isNotEmpty() },
                status = authStatus,
                clientSecret = data.optString("client_secret", "").takeIf { it.isNotEmpty() },
                amount = data.optInt("amount", -1).takeIf { it >= 0 },
                currency = data.optString("currency", "").takeIf { it.isNotEmpty() },
                authenticationConnector = data.optString("authentication_connector", "")
                    .takeIf { it.isNotEmpty() },
                force3dsChallenge = data.optBoolean("force_3ds_challenge", false),
                returnUrl = data.optString("return_url", "").takeIf { it.isNotEmpty() },
                createdAt = data.optString("created_at", "").takeIf { it.isNotEmpty() },
                profileId = data.optString("profile_id", "").takeIf { it.isNotEmpty() },
                psd2ScaExemptionType = data.optString("psd2_sca_exemption_type", "")
                    .takeIf { it.isNotEmpty() },
                acquirerDetails = acquirerDetails,
                threedsServerTransactionId = data.optString("threeds_server_transaction_id", "")
                    .takeIf { it.isNotEmpty() },
                maximumSupported3dsVersion = data.optString("maximum_supported_3ds_version", "")
                    .takeIf { it.isNotEmpty() },
                connectorAuthenticationId = data.optString("connector_authentication_id", "")
                    .takeIf { it.isNotEmpty() },
                threeDsMethodData = data.optString("three_ds_method_data", "")
                    .takeIf { it.isNotEmpty() },
                threeDsMethodUrl = data.optString("three_ds_method_url", "")
                    .takeIf { it.isNotEmpty() },
                messageVersion = data.optString("message_version", "").takeIf { it.isNotEmpty() },
                connectorMetadata = data.optString("connector_metadata", "")
                    .takeIf { it.isNotEmpty() },
                directoryServerId = data.optString("directory_server_id", "")
                    .takeIf { it.isNotEmpty() },
                vaultTokenData = vaultTokenData,
                billing = data.optString("billing", "").takeIf { it.isNotEmpty() },
                shipping = data.optString("shipping", "").takeIf { it.isNotEmpty() },
                browserInformation = data.optString("browser_information", "")
                    .takeIf { it.isNotEmpty() },
                email = data.optString("email", "").takeIf { it.isNotEmpty() },
                transStatus = data.optString("trans_status", "").takeIf { it.isNotEmpty() },
                acsUrl = data.optString("acs_url", "").takeIf { it.isNotEmpty() },
                challengeRequest = data.optString("challenge_request", "")
                    .takeIf { it.isNotEmpty() },
                acsReferenceNumber = data.optString("acs_reference_number", "")
                    .takeIf { it.isNotEmpty() },
                acsTransId = data.optString("acs_trans_id", "").takeIf { it.isNotEmpty() },
                acsSignedContent = data.optString("acs_signed_content", "")
                    .takeIf { it.isNotEmpty() },
                threeDsRequestorUrl = data.optString("three_ds_requestor_url", "")
                    .takeIf { it.isNotEmpty() },
                threeDsRequestorAppUrl = data.optString("three_ds_requestor_app_url", "")
                    .takeIf { it.isNotEmpty() },
                eci = data.optString("eci", "").takeIf { it.isNotEmpty() },
                errorMessage = data.optString("error_message", "").takeIf { it.isNotEmpty() },
                errorCode = data.optString("error_code", "").takeIf { it.isNotEmpty() },
                profileAcquirerId = data.optString("profile_acquirer_id", "")
                    .takeIf { it.isNotEmpty() }
            )
        }
    }
}
