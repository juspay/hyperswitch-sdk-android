package io.hyperswitch.click_to_pay

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.widget.FrameLayout
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
 * Default implementation of ClickToPaySessionLauncher
 * Handles Click to Pay session lifecycle and WebView-based operations
 */
class DefaultClickToPaySessionLauncher(
    private val activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
    private val customParams: Bundle? = null,
): ClickToPaySessionLauncher {
    private var parentView: FrameLayout? = null
    private lateinit var hSWebViewManagerImpl: HSWebViewManagerImpl
    private lateinit var hSWebViewWrapper: HSWebViewWrapper
    private var isSdkInitialized = false
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()

    /**
     * Initialize the Click to Pay session
     */
    init {
        activity.runOnUiThread {
            val onMessage = object: Callback {
                override fun invoke(args: Map<String, Any?>) {
                    println(args)
                    (args["data"] as? String)?.let { jsonString ->
                        val jsonObject = JSONObject(jsonString)

                        val requestId = jsonObject.optString("requestId", "")
                        if (requestId.isNotEmpty()) {
                            pendingRequests.remove(requestId)?.resume(jsonString)
                        } else {
                            if (jsonObject.has("sdkInitialised")) {
                                println("SDK Initialised: ${jsonObject.getBoolean("sdkInitialised")}")
                                isSdkInitialized = jsonObject.getBoolean("sdkInitialised")
                            }
                            if (jsonObject.has("clickToPaySession")) {
                                println("Click to Pay Session: ${jsonObject.getBoolean("clickToPaySession")}")
                            }else{
                            }
                        }
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

            hSWebViewWrapper.layoutParams = LayoutParams(
                1,
                1
            )
            hSWebViewWrapper.visibility = View.GONE

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(hSWebViewWrapper)
            loadUrl()
        }
    }

    private fun loadUrl() {

        val baseHtml = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <script>
                  function handleScriptError() {
                      console.error('Failed to load HyperLoader.js');
                      window.HSAndroidInterface.postMessage(JSON.stringify({
                          "sdkInitialised": false,
                          "error": "Script load failed"
                      }));
                  }
            
                  async function initHyper() {
                      try {                      
                          if (typeof Hyper === 'undefined') {
                              window.HSAndroidInterface.postMessage(JSON.stringify({
                                  "sdkInitialised": false,
                                  "error": "Hyper is not defined"
                              }));
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
                              "sdkInitialised": true
                          }));
                      } catch (error) {
                          console.error('Hyper initialization failed:', error);
                          window.HSAndroidInterface.postMessage(JSON.stringify({
                              "sdkInitialised": false,
                              "error": error.message
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
//        map.putString("uri", "https://google.com")
        map.putString("html", baseHtml)
        map.putString("baseUrl", "https://secure.checkout.visa.com")
        hSWebViewManagerImpl.loadSource(hSWebViewWrapper, map)
    }

    override suspend fun initClickToPaySession(
        clientSecret: String?,
        profileId: String?,
        authenticationId: String?,
        merchantId: String?,
        request3DSAuthentication: Boolean
    ) = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
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
                        if (!authenticationSession){
                            throw Exception("Authentication session is empty");
                            return;
                        }
                        window.clickToPaySession = await authenticationSession.initClickToPaySession({
                              request3DSAuthentication: $request3DSAuthentication,
                        });
                        if (window.clickToPaySession && window.clickToPaySession["error"]){
                         window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: { error : window.clickToPaySession["error"], success: false }
                        }));
                        }else{
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: { success: true }
                        }));
                        }
                    } catch (error) {
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: { success: false, error: error.message }
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        val success = data.optBoolean("success", false)
        if (!success) {
            val error = data.optJSONObject("error")
            throw Exception("Failed to initialize Click to Pay session: $error")
        }
    }

    /**
     * Check if a customer has an existing Click to Pay profile
     */
    override suspend fun isCustomerPresent(request: CustomerPresenceRequest?): CustomerPresenceResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    try{
                    const isCustomerPresent = await window.clickToPaySession.isCustomerPresent({
                        ${request?.email?.let {
                            "email: \"${request.email}\""
                        }?:""}
                    });
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: isCustomerPresent
                    }));
                    }catch(e){
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                                error : "clickToPaySession is not properly initialized"
                        }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        if (!jsonObject.isNull("error")){
            val error = jsonObject.getString("error")
            throw Exception("Failed to check customer presence: $error")
        }
        val data = jsonObject.getJSONObject("data")
        return@withContext CustomerPresenceResponse(
            customerPresent = data.optBoolean("customerPresent", false)
        )
    }

    /**
     * Retrieve the status of customer's saved cards
     */
    override suspend fun getUserType(): CardsStatusResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                try{
                    const userType = await window.clickToPaySession.getUserType();
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: userType
                    }));
                    }catch(e){
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: {
                        error : e?.message
                        }
                    }));
                    }
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        if (!jsonObject.isNull("error")){
            val error = jsonObject.getString("error")
            throw Exception("Failed to get user type: $error")
        }
        val data = jsonObject.getJSONObject("data")
        val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT")
        return@withContext CardsStatusResponse(
            statusCode = StatusCode.valueOf(statusCodeStr)
        )
    }

    /**
     * Get the list of recognized cards for the customer
     */
    override suspend fun getRecognizedCards(): List<RecognizedCard>? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    const cards = await window.clickToPaySession.getRecognizedCards();
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: cards
                    }));
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val cardsArray = jsonObject.getJSONArray("data")
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
     * Validate customer authentication with OTP
     */
    override suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard>? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    const cards = await window.clickToPaySession.validateCustomerAuthentication({
                        value: "$otpValue"
                    });
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: cards
                    }));
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
        val cardsArray = jsonObject.getJSONArray("data")
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
     * Checkout with a selected card
     */
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation

            val jsCode = """
                (async function() {
                    const checkoutResponse = await window.clickToPaySession.checkoutWithCard({
                        srcDigitalCardId: "${request.srcDigitalCardId}",
                        rememberMe: ${request.rememberMe}
                    });
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: checkoutResponse
                    }));
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        // Parse the response
        val jsonObject = JSONObject(responseJson)
        val data = jsonObject.getJSONObject("data")
        
        // Parse token_data if present
        val tokenDataObj = data.optJSONObject("token_data")
        val tokenData = tokenDataObj?.let {
            TokenData(
                networkToken = it.optString("network_token", ""),
                cryptogram = it.optString("cryptogram", ""),
                tokenExpirationMonth = it.optString("token_expiration_month", ""),
                tokenExpirationYear = it.optString("token_expiration_year", "")
            )
        }
        
        // Parse acquirer_details if present
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
