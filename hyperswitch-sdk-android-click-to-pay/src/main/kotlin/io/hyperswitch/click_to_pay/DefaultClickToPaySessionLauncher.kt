package io.hyperswitch.click_to_pay

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.webview.utils.Arguments
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
 * @property isWebViewInitialized Thread-safe flag for WebView initialization status
 * @property isDestroyed Flag to prevent operations after cleanup
 * @property originalAccessibility Map to restore view accessibility settings
 * @property REQUEST_TIMEOUT_MS Timeout for JavaScript operations to prevent infinite hangs
 */
class DefaultClickToPaySessionLauncher(
    activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
    private val customParams: Bundle? = null,
) : ClickToPaySessionLauncher {
    // Use WeakReference to prevent memory leak from holding Activity reference
    private val activityRef = WeakReference(activity)

    // Nullable to avoid lateinit crashes and allow proper cleanup
    private var hSWebViewManagerImpl: HSWebViewManagerImpl? = null
    private var hSWebViewWrapper: HSWebViewWrapper? = null

    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<String>>()

    @Volatile
    private var isWebViewInitialized = false

    @Volatile
    private var isDestroyed = false

    private val originalAccessibility = WeakHashMap<View, Int>()

    // Timeout to prevent infinite hangs
    private companion object {
        const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds
    }

    /**
     * Helper function to execute JavaScript on the Main thread and return the response.
     * This ensures WebView operations happen on the correct thread while keeping
     * JSON parsing and data transformation on background threads.
     * Includes timeout mechanism to prevent infinite hangs.
     *
     * @param requestId Unique identifier for tracking this request
     * @param jsCode The JavaScript code to execute
     * @return The JSON response string from the WebView
     * @throws ClickToPayException if operation times out or fails
     */
    private suspend fun evaluateJavascriptOnMainThread(requestId: String, jsCode: String): String {
        return withContext(Dispatchers.Main) {
            withTimeout(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    // Store continuation
                    pendingRequests[requestId] = continuation

                    // Setup cancellation handler to clean up on cancellation
                    continuation.invokeOnCancellation {
                        pendingRequests.remove(requestId)
                    }

                    try {
                        val webViewManager = hSWebViewManagerImpl
                        val webViewWrapper = hSWebViewWrapper

                        // Check if WebView is still available
                        if (webViewManager == null || webViewWrapper == null) {
                            pendingRequests.remove(requestId)
                            continuation.resumeWithException(
                                ClickToPayException(
                                    "WebView not initialized or has been destroyed",
                                    "WEBVIEW_NOT_AVAILABLE"
                                )
                            )
                            return@suspendCancellableCoroutine
                        }

                        webViewManager.evaluateJavascriptWithFallback(webViewWrapper, jsCode)
                    } catch (e: Exception) {
                        pendingRequests.remove(requestId)
                        continuation.resumeWithException(
                            ClickToPayException(
                                "Failed to evaluate JavaScript: ${e.message}",
                                "JAVASCRIPT_EVALUATION_ERROR"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun parseRecognizedCard(cardObj: JSONObject): RecognizedCard {
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
            paymentCardDescriptor = CardType.from(
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
            digitalCardFeatures = cardObj.optJSONObject("digitalCardFeatures")?.let { emptyMap() })
    }


    /**
     * Sets modal accessibility mode by hiding all views except the target view.
     * This implementation correctly handles view hierarchy by not hiding ancestors
     * of the target view, preventing the target from becoming inaccessible.
     *
     * @param root The root ViewGroup to start the traversal from
     * @param targetView The view that should remain accessible
     */
    private fun setModalAccessibility(root: ViewGroup, targetView: View) {
        val ancestors = HashSet<View>()
        var parent = targetView.parent
        while (parent is View) {
            ancestors.add(parent as View)
            parent = parent.parent
        }
        hideViewsRecursively(root, targetView, ancestors)
    }

    /**
     * Recursively hides views from accessibility services while preserving
     * the target view and its ancestor chain.
     *
     * @param currentView The current view being processed
     * @param targetView The view that should remain accessible
     * @param ancestors Set of ancestor views that must not be hidden
     */
    private fun hideViewsRecursively(
        currentView: View,
        targetView: View,
        ancestors: HashSet<View>
    ) {
        if (currentView == targetView) {
            return
        }

        if (ancestors.contains(currentView)) {
            if (currentView is ViewGroup) {
                for (i in 0 until currentView.childCount) {
                    hideViewsRecursively(currentView.getChildAt(i), targetView, ancestors)
                }
            }
        } else {
            if (!originalAccessibility.containsKey(currentView)) {
                originalAccessibility[currentView] = currentView.importantForAccessibility
            }
            currentView.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
       }
    }

    /**
     * Restores the original accessibility settings for all modified views.
     * This method should be called when modal accessibility mode is no longer needed.
     */
    private fun restoreAccessibility() {
        for ((view, originalValue) in originalAccessibility) {
            view.importantForAccessibility = originalValue
        }
        originalAccessibility.clear()
    }


    /**
     * Ensures the instance has not been destroyed.
     * @throws ClickToPayException if instance has been destroyed
     */
    private fun ensureNotDestroyed() {
        if (isDestroyed) {
            throw ClickToPayException(
                "ClickToPaySessionLauncher has been destroyed and cannot be used",
                "INSTANCE_DESTROYED"
            )
        }
    }

    /**
     * Initializes the WebView components asynchronously on the main thread.
     * This method is idempotent and can be called multiple times safely.
     * Uses proper synchronization to prevent race conditions.
     *
     * @throws ClickToPayException if WebView initialization fails
     */
    private suspend fun ensureWebViewInitialized() {
        // Early return if already initialized
        if (isWebViewInitialized) return

        withContext(Dispatchers.Main) {
            // Double-check pattern with proper synchronization
            synchronized(this@DefaultClickToPaySessionLauncher) {
                if (isWebViewInitialized) return@withContext

                val activity = activityRef.get() ?: run {
                    throw ClickToPayException(
                        "Activity reference has been garbage collected",
                        "ACTIVITY_NOT_AVAILABLE"
                    )
                }

                val onMessage = Callback { args ->
                    try {
                        (args["data"] as? String)?.let { jsonString ->
                            val jsonObject = JSONObject(jsonString)
                            val requestId = jsonObject.optString("requestId", "")
                            if (requestId.isNotEmpty()) {
                                // Resume continuation and remove from map
                                pendingRequests.remove(requestId)?.resume(jsonString)
                            }
                        }
                    } catch (e: Exception) {
                        // Log error but don't crash
                        e.printStackTrace()
                    }
                }

                val webViewManager = HSWebViewManagerImpl(activity, onMessage)
                val webViewWrapper = webViewManager.createViewInstance()

                webViewManager.setJavaScriptEnabled(webViewWrapper, true)
                webViewManager.setMessagingEnabled(webViewWrapper, true)
                webViewManager.setJavaScriptCanOpenWindowsAutomatically(webViewWrapper, true)
                webViewManager.setScalesPageToFit(webViewWrapper, true)
                webViewManager.setMixedContentMode(webViewWrapper, "compatibility")
                webViewManager.setThirdPartyCookiesEnabled(webViewWrapper, true)
                webViewManager.setCacheEnabled(webViewWrapper, true)
                webViewWrapper.isFocusable = false
                webViewWrapper.isFocusableInTouchMode = false
                webViewWrapper.layoutParams = LayoutParams(1, 1)
                webViewWrapper.contentDescription = "Click to Pay"
                webViewWrapper.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                    ?: throw ClickToPayException(
                        "Could not find root view in activity",
                        "ROOT_VIEW_NOT_FOUND"
                    )
                rootView.addView(webViewWrapper)

                // Assign to instance variables only after successful initialization
                hSWebViewManagerImpl = webViewManager
                hSWebViewWrapper = webViewWrapper
                isWebViewInitialized = true
            }
        }
    }

    /**
     * Initializes the Click to Pay SDK by loading the HyperLoader script.
     *
     * Creates an HTML page with the HyperLoader.js script and initializes
     * the Hyper instance with the provided configuration.
     *
     * @throws ClickToPayException if SDK initialization fails with error details
     */
    @Throws(ClickToPayException::class)
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
     * @throws ClickToPayException if script loading or initialization fails
     */
    private suspend fun loadUrl(requestId: String = UUID.randomUUID().toString()) {
        val baseHtml =
            "<!DOCTYPE html><html><head><script>function handleScriptError(){console.error('ClickToPay','Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='https://beta.hyperswitch.io/web/2025.11.28.00/v1/HyperLoader.js' onload='initHyper()' onerror='handleScriptError()' async></script></head><body></body></html>".trimMargin()

        val responseJson = withContext(Dispatchers.Main) {
            withTimeout(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    pendingRequests[requestId] = continuation

                    continuation.invokeOnCancellation {
                        pendingRequests.remove(requestId)
                    }

                    try {
                        val webViewManager = hSWebViewManagerImpl
                        val webViewWrapper = hSWebViewWrapper

                        if (webViewManager == null || webViewWrapper == null) {
                            pendingRequests.remove(requestId)
                            continuation.resumeWithException(
                                ClickToPayException(
                                    "WebView not initialized",
                                    "WEBVIEW_NOT_AVAILABLE"
                                )
                            )
                            return@suspendCancellableCoroutine
                        }

                        val map = Arguments.createMap()
                        map.putString("html", baseHtml)
                        map.putString("baseUrl", "https://secure.checkout.visa.com")
                        webViewManager.loadSource(webViewWrapper, map)
                    } catch (e: Exception) {
                        pendingRequests.remove(requestId)
                        continuation.resumeWithException(
                            ClickToPayException(
                                "Failed to load source: ${e.message}",
                                "LOAD_SOURCE_ERROR"
                            )
                        )
                    }
                }
            }
        }

        withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                throw ClickToPayException(
                    "Failed to load URL - Type: $errorType, Message: $errorMessage",
                    "SCRIPT_LOAD_ERROR"
                )
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
     * @throws ClickToPayException if session initialization fails
     */
    @Throws(ClickToPayException::class)
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
                throw ClickToPayException(
                    "Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_SESSION_ERROR"
                )
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
     * @throws ClickToPayException if the check fails
     */
    @Throws(ClickToPayException::class)
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
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown Error")
                throw ClickToPayException(
                    "Failed to get customer present: $errorMessage", errorType
                )
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
     * @throws ClickToPayException if retrieval fails
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
                val errorMessage = error.optString("message", "Unknown Error")
                throw ClickToPayException(
                    message = "Failed to get user type : $errorMessage", typeString
                )
            }

            val statusCodeStr = safeReturnStringValue(data, "statusCode") ?: "NO_CARDS_PRESENT"
            CardsStatusResponse(
                statusCode = StatusCode.from(statusCodeStr.uppercase())
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
     * @throws ClickToPayException if card retrieval fails
     */
    @Throws(ClickToPayException::class)
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
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                throw ClickToPayException(
                    "Failed to get recognized cards - Type: $errorType, Message: $errorMessage",
                    errorType
                )
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
     * @throws ClickToPayException if OTP validation fails
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

                throw ClickToPayException(
                    errorMessage, typeString
                )
            }

            val cardsArray = data as org.json.JSONArray
            (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(cardsArray.getJSONObject(i))
            }
        }
    }

    private fun safeReturnStringValue(
        obj: JSONObject, key: String
    ): String? {
        return obj.optString(key).takeIf { it.isNotEmpty() && it != "null" }
    }


    private fun parsePaymentData(obj: JSONObject?): PaymentData? {
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


    /**
     * Processes checkout with a selected card.
     *
     * Initiates payment processing using the customer's selected Click to Pay card.
     * Parses the complete checkout response including transaction details,
     * token data, and 3DS information.
     *
     * @param request CheckoutRequest containing card ID and preferences
     * @return CheckoutResponse with complete transaction details
     * @throws ClickToPayException if checkout fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        ensureWebViewInitialized()

        val activity = activityRef.get() ?: throw ClickToPayException(
            "Activity reference has been garbage collected",
            "ACTIVITY_NOT_AVAILABLE"
        )

        val webViewWrapper = hSWebViewWrapper ?: throw ClickToPayException(
            "WebView not initialized",
            "WEBVIEW_NOT_AVAILABLE"
        )

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw ClickToPayException(
                "Could not find root view in activity",
                "ROOT_VIEW_NOT_FOUND"
            )

        setModalAccessibility(rootView, webViewWrapper)

        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const checkoutResponse=await window.ClickToPaySession.checkoutWithCard({srcDigitalCardId:'${request.srcDigitalCardId}',rememberMe:${request.rememberMe}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:checkoutResponse}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'CheckoutWithCardError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        restoreAccessibility()
        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                throw ClickToPayException(errorMessage, errorType)
            }

            // SUCCESS: Parse the response
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
            } catch (e: IllegalArgumentException) {
                null
            }

            val response = CheckoutResponse(
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
                    data,
                    "threeDsServerTransactionId"
                ),
                maximumSupported3dsVersion = safeReturnStringValue(
                    data,
                    "maximumSupported3dsVersion"
                ),
                connectorAuthenticationId = safeReturnStringValue(
                    data,
                    "connectorAuthenticationId"
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

            // Call destroy() only on a successful response
            destroy()
            return@withContext response
        }
    }

    private suspend fun destroy() {
        try {
            pendingRequests.values.forEach { it.cancel() }
            pendingRequests.clear()

            restoreAccessibility()

            val activity = activityRef.get()
            val wrapper = hSWebViewWrapper

            if (activity != null && wrapper != null) {
                withContext(Dispatchers.Main) {
                    if (wrapper.parent is ViewGroup) {
                        (wrapper.parent as ViewGroup).removeView(wrapper)
                    }
                    hSWebViewManagerImpl
                    wrapper.webView.stopLoading()
                    wrapper.webView.loadUrl("about:blank")
                    wrapper.webView.clearHistory()
                    wrapper.webView.clearCache(true)
                    wrapper.webView.removeAllViews()
                    wrapper.webView.destroy()
                }
            }

        } catch (_: Exception) {
        }

        // Null references
        hSWebViewWrapper = null
        hSWebViewManagerImpl = null
        isWebViewInitialized = false
    }


    /**
     * Processes signOut to clear the cookies.
     *
     * @return SignOutResponse with transaction details and status
     * @throws ClickToPayException if checkout fails
     */
    override suspend fun signOut(): SignOutResponse {
        ensureWebViewInitialized()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const signOutResponse = await window.ClickToPaySession.signOut();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data: signOutResponse }));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'SignOutError',message:error.message}}}))}})();".trimMargin()
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.optJSONObject("data")
            val error = data?.optJSONObject("error")
            if (error != null) {
                val errorMessage = error.optString(
                    "message", "SignOut Error"
                )
                val errorType = error.optString("type", "SignOutError")
                throw ClickToPayException(
                    "Failed to SignOut : $errorMessage", errorType
                )
            }
            SignOutResponse(
                recognized = data?.optBoolean("recognized", false) ?: false
            )
        }
    }
}
