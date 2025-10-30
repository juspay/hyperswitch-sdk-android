package io.hyperswitch.click_to_pay

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()

    /**
     * Initialize the Click to Pay session
     */
    init {
        activity.runOnUiThread {
            parentView = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                visibility = View.GONE
            }

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
                            }
                            if (jsonObject.has("clickToPaySession")) {
                                println("Click to Pay Session: ${jsonObject.getBoolean("clickToPaySession")}")
                            }
                        }
                    }
                }
            }

            hSWebViewManagerImpl = HSWebViewManagerImpl(activity, onMessage)
            hSWebViewWrapper = hSWebViewManagerImpl.createViewInstance()

            hSWebViewManagerImpl.setJavaScriptEnabled(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setMessagingEnabled(hSWebViewWrapper, true)
            hSWebViewManagerImpl.setScalesPageToFit(hSWebViewWrapper, true)

            hSWebViewWrapper.webView.setBackgroundColor(Color.RED)

            parentView?.addView(hSWebViewWrapper)

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(parentView)
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
                        window.clickToPaySession = await authenticationSession.initClickToPaySession({
                              request3DSAuthentication: $request3DSAuthentication,
                        });
                        window.HSAndroidInterface.postMessage(JSON.stringify({
                            requestId: "$requestId",
                            data: { success: true }
                        }));
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
            val error = data.optString("error", "Unknown error")
            throw Exception("Failed to initialize Click to Pay session: $error")
        }
    }

    /**
     * Check if a customer has an existing Click to Pay profile
     */
    override suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse? = withContext(Dispatchers.Main) {
        val requestId = UUID.randomUUID().toString()
        
        val responseJson = suspendCancellableCoroutine<String> { continuation ->
            pendingRequests[requestId] = continuation
            
            val jsCode = """
                (async function() {
                    const isCustomerPresent = await window.clickToPaySession.isCustomerPresent({
                        ${request.email?.let {
                            "email: \"${request.email}\""
                        }?:""}
                    });
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: isCustomerPresent
                    }));
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
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
                    const userType = await window.clickToPaySession.getUserType();
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: "$requestId",
                        data: userType
                    }));
                })();
            """.trimIndent()
            hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
        }
        
        val jsonObject = JSONObject(responseJson)
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
//        val data = jsonObject.getJSONObject("data")
//        val tokenDataObj = data.getJSONObject("tokenData")
//
//        return@withContext CheckoutResponse(
//            authenticationId = data.getString("authenticationId"),
//            merchantId = data.getString("merchantId"),
//            status = data.getString("status"),
//            clientSecret = data.getString("clientSecret"),
//            amount = data.getInt("amount"),
//            currency = data.getString("currency"),
//            tokenData = TokenData(
//                networkToken = tokenDataObj.getString("networkToken"),
//                tavv = tokenDataObj.getString("tavv"),
//                tokenExpirationMonth = tokenDataObj.getString("tokenExpirationMonth"),
//                tokenExpirationYear = tokenDataObj.getString("tokenExpirationYear")
//            ),
//            transStatus = data.getString("transStatus")
//        )
        return@withContext null
    }
}
