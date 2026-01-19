package io.hyperswitch.click_to_pay

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.logs.SDKEnvironment
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
    private var activity: Activity,
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

    @Volatile
    private var isDestroyed = false

    @Volatile
    private var isWebViewAttached = false

    private val originalAccessibility = HashMap<View, Int>()

    private var clientSecret: String? = null
    private var profileId: String? = null
    private var authenticationId: String? = null
    private var merchantId: String? = null

    private fun logData(
        type: String,
        value: String,
        category: LogCategory = LogCategory.USER_EVENT
    ) {
        val log = HSLog.LogBuilder().logType(type).category(category)
            .eventName(EventName.CLICK_TO_PAY_FLOW)
            .value(value)
            .version(BuildConfig.VERSION_NAME)
            .paymentId(this.authenticationId ?: "")
            .sessionId(this.clientSecret ?: "")
        HyperLogManager.addLog(log.build())
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
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestId] = continuation
                hSWebViewManagerImpl.evaluateJavascriptWithFallback(hSWebViewWrapper, jsCode)
            }
        }
    }


    private fun getHyperLoaderURL(): String {
        return if (getEnvironment(publishableKey) == SDKEnvironment.PROD) {
            "https://checkout.hyperswitch.io/web/2025.11.28.01/v1/HyperLoader.js"
        } else {
            "https://beta.hyperswitch.io/web/2025.11.28.01/v1/HyperLoader.js"
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
     * Detaches the WebView from the view hierarchy to pause JavaScript execution.
     * This triggers the same lifecycle behavior as backgrounding an app,
     * automatically pausing timers, network requests, and all JavaScript operations.
     */
    private suspend fun detachWebView() {
        withContext(Dispatchers.Main) {
            if (isWebViewInitialized && isWebViewAttached) {
                val parent = hSWebViewWrapper.parent
                if (parent is ViewGroup) {
                    parent.removeView(hSWebViewWrapper)
                    isWebViewAttached = false
                    logData("INFO", "WEBVIEW | DETACHED | JS execution paused")
                }
            }
        }
    }

    /**
     * Reattaches the WebView to the view hierarchy to resume JavaScript execution.
     * This automatically resumes all paused operations.
     */
    private suspend fun reattachWebView() {
        withContext(Dispatchers.Main) {
            if (isWebViewInitialized && !isWebViewAttached) {
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView.addView(hSWebViewWrapper)
                isWebViewAttached = true
                logData("INFO", "WEBVIEW | REATTACHED | JS execution resumed")
            }
        }
    }

    /**
     * Cancels all pending requests to prevent stale callbacks from executing.
     *
     * @param errorMessage Optional error message for cancellation
     */
    private fun cancelPendingRequests(errorMessage: String = "Operation cancelled due to error") {
        if (pendingRequests.isNotEmpty()) {
            logData("INFO", "WEBVIEW | Cancelling ${pendingRequests.size} pending requests")
            pendingRequests.values.forEach { continuation ->
                continuation.cancel(kotlinx.coroutines.CancellationException(errorMessage))
            }
            pendingRequests.clear()
        }
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
     * Ensures the session is ready for operations.
     * Combines three essential checks:
     * 1. Ensures the session hasn't been destroyed
     * 2. Ensures the WebView is initialized
     * 3. Ensures the WebView is attached and ready
     *
     * @throws ClickToPayException if the session is destroyed or initialization fails
     */
    private suspend fun ensureReady() {
        ensureNotDestroyed()
        ensureWebViewInitialized()
        reattachWebView()
    }

    /**
     * Initializes the WebView components asynchronously on the main thread.
     * This method is idempotent and can be called multiple times safely.
     * If the session was previously closed, this will reinitialize it.
     *
     * @throws ClickToPayException if WebView initialization fails
     */
    private suspend fun ensureWebViewInitialized(allowReinitialize: Boolean = false) {
        if (isWebViewInitialized && !allowReinitialize) return

        withContext(Dispatchers.Main) {
            if (isWebViewInitialized && !allowReinitialize) return@withContext
            logData("INFO", "WEBVIEW | INIT")
            val onMessage = Callback { args ->
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
            hSWebViewWrapper.isFocusable = false
            hSWebViewWrapper.isFocusableInTouchMode = false
            hSWebViewWrapper.layoutParams = LayoutParams(1, 1)
            hSWebViewWrapper.contentDescription = "Click to Pay"
            hSWebViewWrapper.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(hSWebViewWrapper)
            isWebViewAttached = true

            isWebViewInitialized = true
            logData("INFO", "WEBVIEW | INIT | SUCCESS")
        }
    }

    /**
     * Initializes the Click to Pay SDK by loading the HyperLoader script.
     *
     * Creates an HTML page with the HyperLoader.js script and initializes
     * the Hyper instance with the provided configuration.
     *
     * Can be called again after close() to reinitialize the session.
     *
     * @throws ClickToPayException if SDK initialization fails with error details
     */
    @Throws(ClickToPayException::class)
    override suspend fun initialize() {
        val loggingEndPoint = if (customLogUrl != "" && customLogUrl != null) {
            customLogUrl
        } else {
            getLoggingUrl(publishableKey)
        }
        HyperLogManager.initialise(publishableKey, loggingEndPoint)

        // Allow reinitialization if the session was previously closed
        if (isDestroyed) {
            isDestroyed = false
            isWebViewInitialized = false
            isWebViewAttached = false
        }

        ensureWebViewInitialized(allowReinitialize = true)
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

        val baseUrl = if (getEnvironment(publishableKey) == SDKEnvironment.PROD) {
            "https://secure.checkout.visa.com"
        } else {
            "https://sandbox.secure.checkout.visa.com"
        }
        val hyperLoaderUrl = getHyperLoaderURL()
        logData("INFO", "WEBVIEW | LOADING | $hyperLoaderUrl with $baseUrl")
        val baseHtml =
            "<!DOCTYPE html><html><head><script>function handleScriptError(){console.error('ClickToPay','Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='${hyperLoaderUrl}' onload='initHyper()' onerror='handleScriptError()' async></script></head><body></body></html>"

        val responseJson = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestId] = continuation

                val map = Arguments.createMap()
                map.putString("html", baseHtml)
                map.putString("baseUrl", baseUrl)
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
                logData(
                    "ERROR",
                    "WEBVIEW | URL | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to load URL - Type: $errorType, Message: $errorMessage",
                    "SCRIPT_LOAD_ERROR"
                )
            }
            logData("INFO", "WEBVIEW | Script loaded")
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

        this.clientSecret = clientSecret
        this.profileId = profileId
        this.authenticationId = authenticationId
        this.merchantId = merchantId

        ensureReady()
        val requestId = UUID.randomUUID().toString()
        logData("INFO", "C2P | INIT")

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
                logData(
                    "ERROR",
                    "C2P | INIT | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_SESSION_ERROR"
                )
            }
            logData("INFO", "C2P | INIT | SUCCESS")
        }
    }


    override suspend fun getActiveClickToPaySession(activity: Activity) {
        ensureReady()
        try {
            if (this.activity !== activity) {
                logData(
                    "INFO",
                    "ACTIVITY_UPDATE | Switching from ${this.activity.javaClass.simpleName} to ${activity.javaClass.simpleName}"
                )
                if (isWebViewInitialized && isWebViewAttached) {
                    val parent = hSWebViewWrapper.parent
                    if (parent is ViewGroup) {
                        withContext(Dispatchers.Main) {
                            parent.removeView(hSWebViewWrapper)
                        }
                        isWebViewAttached = false
                        logData("INFO", "WEBVIEW | DETACHED from old activity")
                    }
                }

                    this.activity = activity
                    if (isWebViewInitialized && !isWebViewAttached) {
                        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                        if (rootView != null) {
                            withContext(Dispatchers.Main) {
                                rootView.addView(hSWebViewWrapper)
                            }
                            isWebViewAttached = true
                            logData("INFO", "WEBVIEW | ATTACHED to new activity")
                        } else {
                            logData(
                                "ERROR",
                                "WEBVIEW | Failed to find root view in new activity",
                                LogCategory.USER_ERROR
                            )
                        }
                }
            }
        } catch (_: Exception) {
            throw ClickToPayException("WebView is not found", "C2P_NOT_FOUND")
        }
        val requestId = UUID.randomUUID().toString()
        logData("INFO", "C2P | GET_EXISTING_SESSION")
        val jsCode =
            "(async function(){ try {let authenticationSession=window.hyperInstance.initAuthenticationSession({clientSecret:'$clientSecret',profileId:'$profileId',authenticationId:'$authenticationId',merchantId:'$merchantId'}); window.ClickToPaySession = await authenticationSession?.getActiveClickToPaySession();const data=window.ClickToPaySession.error?window.ClickToPaySession:{success:true};window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:data}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'getActiveClickToPaySessionError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logData(
                    "ERROR",
                    "C2P | GET_EXISTING_SESSION | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_SESSION_ERROR"
                )
            }
            logData("INFO", "C2P | GET_EXISTING_SESSION | SUCCESS")
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
        ensureReady()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const isCustomerPresent=await window.ClickToPaySession.isCustomerPresent({${request.email?.let { "email:'${request.email}'" } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:isCustomerPresent}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'IsCustomerPresentError',message:error.message}}}))}})();"
        logData("INFO", "CUSTOMER_CHECK | INIT")

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown Error")
                logData(
                    "ERROR",
                    "CUSTOMER_CHECK | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to get customer present: $errorMessage", errorType
                )
            }
            logData("INFO", "CUSTOMER_CHECK | SUCCESS")
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
        ensureReady()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const userType=await window.ClickToPaySession.getUserType();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:userType}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"

        logData("INFO", "GET_USER_TYPE | INIT")

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown Error")
                logData(
                    "ERROR",
                    "GET_USER_TYPE | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    message = "Failed to get user type : $errorMessage", errorType
                )
            }
            logData("INFO", "GET_USER_TYPE | SUCCESS")

            val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT").uppercase()
            CardsStatusResponse(
                statusCode = StatusCode.from(statusCodeStr)
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
        ensureReady()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.getRecognizedCards();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'GetRecognizedCardsError',message:error.message}}}))}})();"
        logData("INFO", "GET_CARDS | INIT")

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = data.getJSONObject("error")
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logData(
                    "ERROR",
                    "GET_CARDS | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to get recognized cards - Type: $errorType, Message: $errorMessage",
                    errorType
                )
            }

            val cardsArray = data as org.json.JSONArray
            val cards = (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(cardsArray.getJSONObject(i))
            }
            val visaCount = cards.count{it.paymentCardDescriptor == CardType.VISA}
            val masterCardCount = cards.count{it.paymentCardDescriptor == CardType.MASTERCARD }
            logData("INFO", "GET_CARDS | SUCCESS | visa: $visaCount | mastercard: $masterCardCount")
            cards

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
        ensureReady()
        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.validateCustomerAuthentication({value:'$otpValue'});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"
        logData("INFO", "AUTH_VALIDATION | INIT | Validating otp")
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = data.getJSONObject("error")
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logData(
                    "ERROR",
                    "AUTH_VALIDATION | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    errorMessage, errorType
                )
            }

            val cardsArray = data as org.json.JSONArray
            val cards = (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(cardsArray.getJSONObject(i))
            }
            logData("INFO", "AUTH_VALIDATION | SUCCESS")
            cards
        }
    }

    private fun safeReturnStringValue(
        obj: JSONObject, key: String
    ): String? {
        return when {
            obj.isNull(key) -> null
            else -> obj.getString(key).takeIf { it.isNotEmpty() }
        }
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
        ensureReady()

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        setModalAccessibility(rootView, hSWebViewWrapper)

        val requestId = UUID.randomUUID().toString()

        val jsCode =
            "(async function(){try{const checkoutResponse=await window.ClickToPaySession.checkoutWithCard({srcDigitalCardId:'${request.srcDigitalCardId}',rememberMe:${request.rememberMe}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:checkoutResponse}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'CheckoutWithCardError',message:error.message}}}))}})();"
        logData("INFO", "CHECKOUT | rememberMe: ${request.rememberMe}")
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        restoreAccessibility()
        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")

            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logData(
                    "ERROR", "CHECKOUT | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(errorMessage, errorType)
            }

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

            val response = CheckoutResponse(
                authenticationId = safeReturnStringValue(data, "authenticationId"),
                merchantId = safeReturnStringValue(data, "merchantId"),
                status = authStatus,
                clientSecret = safeReturnStringValue(data, "clientSecret"),
                amount = data.optInt("amount", -1).takeIf { it >= 0 },
                currency = safeReturnStringValue(data, "currency"),
                authenticationConnector = safeReturnStringValue(
                    data,
                    "authenticationConnector",
                ),
                force3dsChallenge = data.optBoolean("force3dsChallenge", false),
                returnUrl = safeReturnStringValue(data, "returnUrl"),
                createdAt = safeReturnStringValue(data, "createdAt"),
                profileId = safeReturnStringValue(data, "profileId"),
                psd2ScaExemptionType = safeReturnStringValue(data, "psd2ScaExemptionType"),
                acquirerDetails = acquirerDetails,
                threedsServerTransactionId = safeReturnStringValue(
                    data,
                    "threeDsServerTransactionId",
                ),
                maximumSupported3dsVersion = safeReturnStringValue(
                    data,
                    "maximumSupported3dsVersion",
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
                threeDsRequestorAppUrl = safeReturnStringValue(
                    data,
                    "threeDsRequestorAppUrl",
                ),
                eci = safeReturnStringValue(data, "eci"),
                errorMessage = safeReturnStringValue(data, "errorMessage"),
                errorCode = safeReturnStringValue(data, "errorCode"),
                profileAcquirerId = safeReturnStringValue(data, "profileAcquirerId")
            )
            logData("INFO", "CHECKOUT | SUCCESS")
            response
        }
    }

    /**
     * Closes and destroys the Click to Pay session.
     *
     * Performs cleanup by:
     * - Cancelling all pending requests
     * - Restoring accessibility settings
     * - Destroying the WebView and its wrapper
     *
     * After calling this method, the session cannot be used again.
     * A new instance must be created for subsequent operations.
     *
     * @throws ClickToPayException if cleanup fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun close() {
        try {
            logData("INFO", "WEBVIEW | CLOSE | INIT")

            pendingRequests.values.forEach { it.cancel() }
            pendingRequests.clear()

            restoreAccessibility()

            withContext(Dispatchers.Main) {
                if (isWebViewInitialized) {
                    if (hSWebViewWrapper.parent is ViewGroup) {
                        (hSWebViewWrapper.parent as ViewGroup).removeView(hSWebViewWrapper)
                        isWebViewAttached = false
                    }
                    hSWebViewWrapper.webView.destroy()
                }
            }

            isWebViewInitialized = false
            isDestroyed = true

            logData("INFO", "WEBVIEW | CLOSE | SUCCESS")
        } catch (e: Exception) {
            logData("ERROR", "WEBVIEW | CLOSE | Message: ${e.message}", LogCategory.USER_ERROR)
            throw ClickToPayException(
                "Failed to close Click to Pay session: ${e.message}",
                "CLOSE_ERROR"
            )
        }
    }


    /**
     * Processes signOut to clear the cookies
     *
     * @return SignOutResponse with transaction details and status
     * @throws ClickToPayException if checkout fails
     */
    override suspend fun signOut(): SignOutResponse {
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const signOutResponse = await window.ClickToPaySession.signOut();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data: signOutResponse }));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'SignOutError',message:error.message}}}))}})();"
        logData("INFO", "SIGN_OUT | INIT")

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = JSONObject(responseJson)
            val data = jsonObject.getJSONObject("data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorMessage = error.optString(
                    "message", "SignOut Error"
                )
                val errorType = error.optString("type", "SignOutError")
                logData(
                    "ERROR",
                    "SIGN_OUT | Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                cancelPendingRequests()
                detachWebView()
                throw ClickToPayException(
                    "Failed to SignOut : $errorMessage", errorType
                )
            }
            logData("INFO", "SIGN_OUT | SUCCESS")
            SignOutResponse(
                recognized = data.optBoolean("recognized", false)
            )
        }
    }
}
