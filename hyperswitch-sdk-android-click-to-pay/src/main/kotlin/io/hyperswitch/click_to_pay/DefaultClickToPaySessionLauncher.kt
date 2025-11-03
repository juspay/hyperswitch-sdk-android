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
): ClickToPaySessionLauncher {
    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper
    
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()

    init {
        activity.runOnUiThread {
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
    private suspend fun loadUrl(requestId: String = UUID.randomUUID().toString()) = withContext(Dispatchers.Main) {
        val responseJson = suspendCancellableCoroutine { continuation ->
            pendingRequests[requestId] = continuation

            val baseHtml = """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <script>
                      function handleScriptError() {
                          console.error('Failed to load HyperLoader.js');
                          window.HSAndroidInterface.postMessage(JSON.stringify({
                              requestId: "$requestId",
                              data: {
                                  error: {
                                      type: "ScriptLoadError",
                                      message: "Failed to load HyperLoader.js"
                                  }
                              }
                          }));
                      }
                
                      async function initHyper() {
                          try {     
                              if (typeof Hyper === 'undefined') {
                                  window.HSAndroidInterface.postMessage(JSON.stringify({
                                      requestId: "$requestId",
                                      data: {
                                          error: {
                                              type: "HyperUndefinedError",
                                              message: "Hyper is not defined"
                                          }
                                      }
                                  }));
                                  return;
                              }
                              window.hyperInstance = Hyper.init("$publishableKey", {
                                  ${customBackendUrl?.let { 
                                      "customBackendUrl: \"$customBackendUrl\","
                                  }?:""}
                                  ${customLogUrl?.let {
                                      "customLogUrl: \"$customLogUrl\","
                                  }?:""}
                              });                
                              window.HSAndroidInterface.postMessage(JSON.stringify({
                                  requestId: "$requestId",
                                  data: {
                                      sdkInitialised: true
                                  }
                              }));
                          } catch (error) {
                              window.HSAndroidInterface.postMessage(JSON.stringify({
                                  requestId: "$requestId",
                                  data: {
                                      error: {
                                          type: "HyperInitializationError",
                                          message: error.message
                                      }
                                  }
                              }));
                          }
                      }
                    </script>
                    <script
                      src="https://beta.hyperswitch.io/v2/HyperLoader.js"
                      onload="initHyper()"
                      onerror="handleScriptError()"
                      async="true"
                    ></script>
                  </head>
                  <body></body>
                </html>
            """.trimIndent()

            val map = Arguments.createMap()
            map.putString("html", baseHtml)
            map.putString("baseUrl", "https://secure.checkout.visa.com")
            hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to load URL - Type: $errorType, Message: $errorMessage")
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
    ) = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try {
                        const authenticationSession = window.hyperInstance.initAuthenticationSession({
                              clientSecret: "$clientSecret",
                              profileId: "$profileId",
                              authenticationId: "$authenticationId",
                              merchantId: "$merchantId",
                        });
                        window.ClickToPaySession = await authenticationSession.initClickToPaySession({
                              request3DSAuthentication: $request3DSAuthentication,
                        });
                        const data = window.ClickToPaySession.error ? window.ClickToPaySession : { success: true }
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: data
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "InitClickToPaySessionError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage")
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
    override suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try {
                        const isCustomerPresent = await window.ClickToPaySession.isCustomerPresent({
                            ${request.email?.let {
                                "email: \"${request.email}\""
                            }?:""}
                        });
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: isCustomerPresent
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "IsCustomerPresentError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to check customer presence - Type: $errorType, Message: $errorMessage")
        }
        
        return@withContext CustomerPresenceResponse(
            customerPresent = data.optBoolean("customerPresent", false)
        )
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
    @Throws(Exception::class)
    override suspend fun getUserType(): CardsStatusResponse = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try {
                        const userType = await window.ClickToPaySession.getUserType();
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: userType
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "GetUserTypeError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to get user type - Type: $errorType, Message: $errorMessage")
        }
        
        val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT")
        return@withContext CardsStatusResponse(
            statusCode = StatusCode.valueOf(statusCodeStr)
        )
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
    override suspend fun getRecognizedCards(): List<RecognizedCard> = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try {
                        const cards = await window.ClickToPaySession.getRecognizedCards();
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: cards
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "GetRecognizedCardsError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.get("data")
        
        if (data is JSONObject && data.has("error")) {
            val error = data.getJSONObject("error")
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to get recognized cards - Type: $errorType, Message: $errorMessage")
        }
        
        val cardsArray = data as org.json.JSONArray
        val cards = mutableListOf<RecognizedCard>()
        
        for (i in 0 until cardsArray.length()) {
            val cardObj = cardsArray.getJSONObject(i)
            val digitalCardDataObj = cardObj.optJSONObject("digitalCardData") ?: JSONObject()
            val maskedBillingAddressObj = cardObj.optJSONObject("maskedBillingAddress")
            
            cards.add(
                RecognizedCard(
                    srcDigitalCardId = cardObj.optString("srcDigitalCardId", ""),
                    panBin = cardObj.optString("panBin", ""),
                    panLastFour = cardObj.optString("panLastFour", ""),
                    tokenLastFour = cardObj.optString("tokenLastFour", ""),
                    digitalCardData = DigitalCardData(
                        status = digitalCardDataObj.optString("status", ""),
                        presentationName = digitalCardDataObj.optString("presentationName", "").takeIf { it.isNotEmpty() },
                        descriptorName = digitalCardDataObj.optString("descriptorName", ""),
                        artUri = digitalCardDataObj.optString("artUri", "")
                    ),
                    panExpirationMonth = cardObj.optString("panExpirationMonth", ""),
                    panExpirationYear = cardObj.optString("panExpirationYear", ""),
                    countryCode = cardObj.optString("countryCode", ""),
                    maskedBillingAddress = maskedBillingAddressObj?.let { obj ->
                        if (obj.length() > 0) {
                            MaskedBillingAddress(
                                name = obj.optString("name", "").takeIf { it.isNotEmpty() },
                                line1 = obj.optString("line1", "").takeIf { it.isNotEmpty() },
                                city = obj.optString("city", "").takeIf { it.isNotEmpty() },
                                state = obj.optString("state", "").takeIf { it.isNotEmpty() },
                                countryCode = obj.optString("countryCode", "").takeIf { it.isNotEmpty() },
                                zip = obj.optString("zip", "").takeIf { it.isNotEmpty() }
                            )
                        } else null
                    }
                )
            )
        }
        
        return@withContext cards
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
    @Throws(Exception::class)
    override suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard> = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try {
                        const cards = await window.ClickToPaySession.validateCustomerAuthentication({
                            value: "$otpValue"
                        });
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: cards
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "ValidateCustomerAuthenticationError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.get("data")
        
        if (data is JSONObject && data.has("error")) {
            val error = data.getJSONObject("error")
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to validate customer authentication - Type: $errorType, Message: $errorMessage")
        }
        
        val cardsArray = data as org.json.JSONArray
        val cards = mutableListOf<RecognizedCard>()
        
        for (i in 0 until cardsArray.length()) {
            val cardObj = cardsArray.getJSONObject(i)
            val digitalCardDataObj = cardObj.optJSONObject("digitalCardData") ?: JSONObject()
            val maskedBillingAddressObj = cardObj.optJSONObject("maskedBillingAddress")
            
            cards.add(
                RecognizedCard(
                    srcDigitalCardId = cardObj.optString("srcDigitalCardId", ""),
                    panBin = cardObj.optString("panBin", ""),
                    panLastFour = cardObj.optString("panLastFour", ""),
                    tokenLastFour = cardObj.optString("tokenLastFour", ""),
                    digitalCardData = DigitalCardData(
                        status = digitalCardDataObj.optString("status", ""),
                        presentationName = digitalCardDataObj.optString("presentationName", "").takeIf { it.isNotEmpty() },
                        descriptorName = digitalCardDataObj.optString("descriptorName", ""),
                        artUri = digitalCardDataObj.optString("artUri", "")
                    ),
                    panExpirationMonth = cardObj.optString("panExpirationMonth", ""),
                    panExpirationYear = cardObj.optString("panExpirationYear", ""),
                    countryCode = cardObj.optString("countryCode", ""),
                    maskedBillingAddress = maskedBillingAddressObj?.let { obj ->
                        if (obj.length() > 0) {
                            MaskedBillingAddress(
                                name = obj.optString("name", "").takeIf { it.isNotEmpty() },
                                line1 = obj.optString("line1", "").takeIf { it.isNotEmpty() },
                                city = obj.optString("city", "").takeIf { it.isNotEmpty() },
                                state = obj.optString("state", "").takeIf { it.isNotEmpty() },
                                countryCode = obj.optString("countryCode", "").takeIf { it.isNotEmpty() },
                                zip = obj.optString("zip", "").takeIf { it.isNotEmpty() }
                            )
                        } else null
                    }
                )
            )
        }
        
        return@withContext cards
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
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation

            val jsCode = """
                (async function() {
                    try {
                        const checkoutResponse = await window.ClickToPaySession.checkoutWithCard({
                            srcDigitalCardId: "${request.srcDigitalCardId}",
                            rememberMe: ${request.rememberMe}
                        });
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: checkoutResponse
                        }));
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: {
                                error: {
                                    type: "CheckoutWithCardError",
                                    message: error.message
                                }
                            }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "Unknown")
            val errorMessage = error.optString("message", "Unknown error")
            throw Exception("Failed to checkout with card - Type: $errorType, Message: $errorMessage")
        }
        
        val tokenDataObj = data.optJSONObject("token_data")
        val tokenData = tokenDataObj?.let {
            TokenData(
                networkToken = it.optString("network_token", ""),
                cryptogram = it.optString("cryptogram", ""),
                tokenExpirationMonth = it.optString("token_expiration_month", ""),
                tokenExpirationYear = it.optString("token_expiration_year", "")
            )
        }
        
        val acquirerDetailsObj = data.optJSONObject("acquirer_details")
        val acquirerDetails = acquirerDetailsObj?.let {
            AcquirerDetails(
                acquirerBin = it.optString("acquirer_bin", "").takeIf { s -> s.isNotEmpty() },
                acquirerMerchantId = it.optString("acquirer_merchant_id", "").takeIf { s -> s.isNotEmpty() },
                merchantCountryCode = it.optString("merchant_country_code", "").takeIf { s -> s.isNotEmpty() }
            )
        }

        return@withContext CheckoutResponse(
            authenticationId = data.optString("authentication_id", ""),
            merchantId = data.optString("merchant_id", ""),
            status = data.optString("status", ""),
            clientSecret = data.optString("client_secret", ""),
            amount = data.optInt("amount", 0),
            currency = data.optString("currency", ""),
            authenticationConnector = data.optString("authentication_connector", ""),
            force3dsChallenge = data.optBoolean("force_3ds_challenge", false),
            returnUrl = data.optString("return_url", "").takeIf { it.isNotEmpty() },
            createdAt = data.optString("created_at", ""),
            profileId = data.optString("profile_id", ""),
            psd2ScaExemptionType = data.optString("psd2_sca_exemption_type", "").takeIf { it.isNotEmpty() },
            acquirerDetails = acquirerDetails,
            threedsServerTransactionId = data.optString("threeds_server_transaction_id", "").takeIf { it.isNotEmpty() },
            maximumSupported3dsVersion = data.optString("maximum_supported_3ds_version", "").takeIf { it.isNotEmpty() },
            connectorAuthenticationId = data.optString("connector_authentication_id", "").takeIf { it.isNotEmpty() },
            threeDsMethodData = data.optString("three_ds_method_data", "").takeIf { it.isNotEmpty() },
            threeDsMethodUrl = data.optString("three_ds_method_url", "").takeIf { it.isNotEmpty() },
            messageVersion = data.optString("message_version", "").takeIf { it.isNotEmpty() },
            connectorMetadata = data.optString("connector_metadata", "").takeIf { it.isNotEmpty() },
            directoryServerId = data.optString("directory_server_id", "").takeIf { it.isNotEmpty() },
            tokenData = tokenData,
            billing = data.optString("billing", "").takeIf { it.isNotEmpty() },
            shipping = data.optString("shipping", "").takeIf { it.isNotEmpty() },
            browserInformation = data.optString("browser_information", "").takeIf { it.isNotEmpty() },
            email = data.optString("email", "").takeIf { it.isNotEmpty() },
            transStatus = data.optString("trans_status", ""),
            acsUrl = data.optString("acs_url", "").takeIf { it.isNotEmpty() },
            challengeRequest = data.optString("challenge_request", "").takeIf { it.isNotEmpty() },
            acsReferenceNumber = data.optString("acs_reference_number", "").takeIf { it.isNotEmpty() },
            acsTransId = data.optString("acs_trans_id", "").takeIf { it.isNotEmpty() },
            acsSignedContent = data.optString("acs_signed_content", "").takeIf { it.isNotEmpty() },
            threeDsRequestorUrl = data.optString("three_ds_requestor_url", "").takeIf { it.isNotEmpty() },
            threeDsRequestorAppUrl = data.optString("three_ds_requestor_app_url", "").takeIf { it.isNotEmpty() },
            eci = data.optString("eci", "").takeIf { it.isNotEmpty() },
            errorMessage = data.optString("error_message", "").takeIf { it.isNotEmpty() },
            errorCode = data.optString("error_code", "").takeIf { it.isNotEmpty() },
            profileAcquirerId = data.optString("profile_acquirer_id", "").takeIf { it.isNotEmpty() }
        )
    }
}
