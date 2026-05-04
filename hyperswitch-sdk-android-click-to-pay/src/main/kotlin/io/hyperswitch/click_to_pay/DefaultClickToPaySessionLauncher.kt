package io.hyperswitch.click_to_pay

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import androidx.webkit.WebViewCompat
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogType
import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.logs.LogUtils.getOrCreateUniqueKey
import io.hyperswitch.logs.SDKEnvironment
import io.hyperswitch.webview.utils.Arguments
import io.hyperswitch.webview.utils.Callback
import io.hyperswitch.webview.utils.HSWebViewManagerImpl
import io.hyperswitch.webview.utils.HSWebViewWrapper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * @property webViewManager Manages WebView lifecycle and operations
 */
class DefaultClickToPaySessionLauncher(
    private var activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
    private val customParams: Bundle? = null,
) : ClickToPaySessionLauncher {
    private val webViewManager = ClickToPayWebViewManager(activity) { type, eventName, value, category ->
        logger(type, eventName, value, category)
    }
    private val isDestroyed = AtomicBoolean(false)
    private val originalAccessibility = HashMap<View, Int>()
    private var authenticationId: String? = null
    private val deviceUniqueSessionId = getOrCreateUniqueKey(activity, "click_to_pay")
    private var sessionId = "${deviceUniqueSessionId}_${UUID.randomUUID()}"
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    private fun logger(
        type: LogType,
        eventName: EventName,
        value: String,
        category: LogCategory = LogCategory.USER_EVENT
    ) {
        val log =
            HSLog.LogBuilder().logType(type).category(category).eventName(eventName).value(value)
                .version(BuildConfig.VERSION_NAME).authenticationId(this.authenticationId ?: "")
                .sessionId(sessionId)
        HyperLogManager.addLog(log.build())
    }

    /**
     * Atomically resumes a pending continuation exactly once.
     */
    private fun resumeContinuation(requestId: String, value: String) {
        webViewManager.resumePendingRequest(requestId, value)
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
     * @throws ClickToPayException if operation times out or fails, or if session is destroyed
     */
    private suspend fun evaluateJavascriptOnMainThread(requestId: String, jsCode: String): String {
        // Guard against destroyed session
        if (isDestroyed.get()) {
            throw ClickToPayException(
                "ClickToPay session has been destroyed",
                "SESSION_DESTROYED"
            )
        }
        return webViewManager.evaluateJavaScript(jsCode, requestId)
    }

    // URL Helpers
    private fun getHyperLoaderURL(): String {
        return if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
            "https://beta.hyperswitch.io/web/2025.11.28.09/v1/HyperLoader.js"
        } else {
            "https://checkout.hyperswitch.io/web/2025.11.28.09/v1/HyperLoader.js"
        }
    }

    // JSON parsing is handled by ClickToPayResponseDecoder

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
        currentView: View, targetView: View, ancestors: HashSet<View>
    ) {
        if (currentView == targetView) return
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
     * Ensures the session is ready for operations.
     * Combines three essential checks:
     * 1. Ensures the session hasn't been destroyed
     * 2. Ensures the WebView is initialized
     * 3. Ensures the WebView is attached and ready
     *
     * @throws ClickToPayException if the session is destroyed or initialization fails
     */
    private suspend fun ensureReady() {
        if (isDestroyed.get()) {
            throw ClickToPayException(
                "ClickToPaySessionLauncher has been destroyed and cannot be used",
                ClickToPayErrorType.INSTANCE_DESTROYED
            )
        }
        webViewManager.ensureInitialized()
        webViewManager.reattach()
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
        if (publishableKey == "null") {
            throw ClickToPayException("Invalid Credentials", ClickToPayErrorType.INVALID_PARAMETER)
        }
        val loggingEndPoint =
            customLogUrl?.takeIf { it.isNotEmpty() } ?: getLoggingUrl(publishableKey)
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(activity.application, BuildConfig.VERSION_NAME, sessionId = sessionId)
        )
        HyperLogManager.initialise(publishableKey, loggingEndPoint)
        HyperLogManager.sendLogsFromFile(LogFileManager(activity))
        if (isDestroyed.get()) {
            isDestroyed.set(false)
            webViewManager.resetState()
        }
        webViewManager.ensureInitialized(allowReinitialize = true)
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
        val baseUrl = if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
            "https://sandbox.secure.checkout.visa.com"
        } else {
            "https://secure.checkout.visa.com"
        }
        val hyperLoaderUrl = getHyperLoaderURL()
        logger(
            LogType.DEBUG,
            EventName.SCRIPT_LOAD_INIT,
            "hyperLoaderUrl: $hyperLoaderUrl, baseUrl: $baseUrl",
            LogCategory.USER_EVENT
        )
        val baseHtml = ClickToPayScripts.createInitializationHtml(
            publishableKey = publishableKey,
            customBackendUrl = customBackendUrl,
            customLogUrl = customLogUrl,
            requestId = requestId,
            hyperLoaderUrl = hyperLoaderUrl
        )

        val responseJson = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                webViewManager.registerPendingRequest(requestId, continuation)
                continuation.invokeOnCancellation { webViewManager.removePendingRequest(requestId) }
                val map = Arguments.createMap()
                map.putString("html", baseHtml)
                map.putString("baseUrl", baseUrl)
                webViewManager.getWebViewManager().loadSource(webViewManager.getWebViewWrapper(), map)
            }
        }

        withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.SCRIPT_LOAD_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.SCRIPT_LOAD_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to load URL - Type: $errorType, Message: $errorMessage", "SCRIPT_LOAD_ERROR")
            }
            logger(LogType.DEBUG, EventName.SCRIPT_LOAD_RETURNED, "success", LogCategory.USER_EVENT)
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
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        request3DSAuthentication: Boolean
    ) {
        logger(
            LogType.DEBUG,
            EventName.INIT_CLICK_TO_PAY_SESSION_INIT,
            "request3DSAuthentication: $request3DSAuthentication",
            LogCategory.USER_EVENT
        )
        this.authenticationId = authenticationId
        this.sessionId = "${deviceUniqueSessionId}_${UUID.randomUUID()}"
        webViewManager.startCapturingCorrelationIds()
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.initClickToPaySession(
            clientSecret = clientSecret,
            profileId = profileId,
            authenticationId = authenticationId,
            merchantId = merchantId,
            request3DSAuthentication = request3DSAuthentication,
            requestId = requestId
        )
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage", "INIT_CLICK_TO_PAY_SESSION_ERROR")
            }
            logger(LogType.DEBUG, EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED, webViewManager.getCorrelationIds().joinToString(", "), LogCategory.USER_EVENT)
            webViewManager.stopCapturingCorrelationIds()
            webViewManager.clearCorrelationIds()
        }
    }


    override suspend fun getActiveClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        activity: Activity
    ) {
        logger(
            LogType.DEBUG,
            EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_INIT,
            "Switching from ${this.activity.javaClass.simpleName} to ${activity.javaClass.simpleName}"
        )
        this.authenticationId = authenticationId
        ensureReady()
        try {
            if (this.activity !== activity) {
                webViewManager.updateActivity(activity)
                this.activity = activity
            }
        } catch (e: Exception) {
            logger(
                LogType.ERROR,
                EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED,
                e.message.toString(),
                LogCategory.USER_ERROR
            )
            throw ClickToPayException("WebView is not found", "C2P_NOT_FOUND")
        }
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.getActiveClickToPaySession(
            clientSecret = clientSecret,
            profileId = profileId,
            authenticationId = authenticationId,
            merchantId = merchantId,
            requestId = requestId
        )
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to get Click to Pay session - Type: $errorType, Message: $errorMessage", "INIT_CLICK_TO_PAY_SESSION_ERROR")
            }
            logger(LogType.DEBUG, EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED, "")
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
        logger(LogType.DEBUG, EventName.IS_CUSTOMER_PRESENT_INIT, "")
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.isCustomerPresent(
            email = request.email,
            requestId = requestId
        )
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.IS_CUSTOMER_PRESENT_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.IS_CUSTOMER_PRESENT_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to get customer present: $errorMessage", errorType)
            }
            when (val result = ClickToPayResponseDecoder.decodeCustomerPresenceResponse(data)) {
                is DecodeResult.Success -> {
                    logger(
                        LogType.DEBUG,
                        EventName.IS_CUSTOMER_PRESENT_RETURNED,
                        "customerPresent: ${result.data.customerPresent}"
                    )
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.IS_CUSTOMER_PRESENT_RETURNED,
                        "Failed to parse customer presence: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse customer presence: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
            }
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
        logger(LogType.DEBUG, EventName.GET_USER_TYPE_INIT, "")
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.getUserType(requestId)
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.GET_USER_TYPE_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.GET_USER_TYPE_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to get user type: $errorMessage", errorType)
            }
            when (val result = ClickToPayResponseDecoder.decodeCardsStatusResponse(data)) {
                is DecodeResult.Success -> {
                    logger(
                        LogType.DEBUG,
                        EventName.GET_USER_TYPE_RETURNED,
                        "statusCode: ${result.data.statusCode}, maskedValidationChannels: ${result.data.maskedValidationChannel}"
                    )
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.GET_USER_TYPE_RETURNED,
                        "Failed to parse cards status: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse cards status: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
            }
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
        logger(LogType.DEBUG, EventName.GET_RECOGNISED_CARDS_INIT, "")
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.getRecognizedCards(requestId)
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.GET_RECOGNISED_CARDS_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = jsonObject.get("data")
            if (data is JSONObject && data.has("error")) {
                val error = ClickToPayResponseDecoder.getNestedObject(data, "error")
                val errorType = ClickToPayResponseDecoder.extractString(error, "type") ?: "ERROR"
                val errorMessage = ClickToPayResponseDecoder.extractString(error, "message") ?: "Unknown error"
                logger(LogType.ERROR, EventName.GET_RECOGNISED_CARDS_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to get recognized cards - Type: $errorType, Message: $errorMessage", errorType)
            }
            val cardsArray = data as JSONArray
            when (val result = ClickToPayResponseDecoder.decodeRecognizedCards(cardsArray)) {
                is DecodeResult.Success -> {
                    val visaCount = result.data.count { it.paymentCardDescriptor == CardType.VISA }
                    val masterCardCount = result.data.count { it.paymentCardDescriptor == CardType.MASTERCARD }
                    logger(
                        LogType.DEBUG,
                        EventName.GET_RECOGNISED_CARDS_RETURNED,
                        "Visa: $visaCount, Mastercard: $masterCardCount"
                    )
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.GET_RECOGNISED_CARDS_RETURNED,
                        "Failed to parse recognized cards: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse recognized cards: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
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
        logger(LogType.DEBUG, EventName.VALIDATE_CUSTOMER_AUTHENTICATION_INIT, "")
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.validateCustomerAuthentication(
            otpValue = otpValue,
            requestId = requestId
        )
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = jsonObject.get("data")
            if (data is JSONObject && data.has("error")) {
                val error = ClickToPayResponseDecoder.getNestedObject(data, "error")
                val errorType = ClickToPayResponseDecoder.extractString(error, "type") ?: "ERROR"
                val errorMessage = ClickToPayResponseDecoder.extractString(error, "message") ?: "Unknown error"
                logger(LogType.ERROR, EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException(errorMessage, errorType)
            }
            val cardsArray = data as JSONArray
            when (val result = ClickToPayResponseDecoder.decodeRecognizedCards(cardsArray)) {
                is DecodeResult.Success -> {
                    val visaCount = result.data.count { it.paymentCardDescriptor == CardType.VISA }
                    val masterCardCount = result.data.count { it.paymentCardDescriptor == CardType.MASTERCARD }
                    logger(
                        LogType.DEBUG,
                        EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED,
                        "Visa: $visaCount, Mastercard: $masterCardCount"
                    )
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED,
                        "Failed to parse validated cards: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse validated cards: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
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
     * @throws ClickToPayException if checkout fails
     */
    @Throws(ClickToPayException::class)
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        logger(LogType.DEBUG, EventName.CHECKOUT_INIT, "rememberMe: ${request.rememberMe}")
        ensureReady()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        setModalAccessibility(rootView, webViewManager.getWebViewWrapper())
        val requestId = UUID.randomUUID().toString()
        logger(LogType.DEBUG, EventName.CREATE_NEW_WEBVIEW_INIT, "")
        val jsCode = ClickToPayScripts.checkoutWithCard(
            srcDigitalCardId = request.srcDigitalCardId,
            rememberMe = request.rememberMe,
            requestId = requestId
        )
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        logger(LogType.DEBUG, EventName.CREATE_NEW_WEBVIEW_RETURNED, "")
        restoreAccessibility()
        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.CHECKOUT_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.CHECKOUT_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException(errorMessage, errorType)
            }
            when (val result = ClickToPayResponseDecoder.decodeCheckoutResponse(data)) {
                is DecodeResult.Success -> {
                    logger(LogType.DEBUG, EventName.CHECKOUT_RETURNED, "status: ${result.data.status}")
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.CHECKOUT_RETURNED,
                        "Failed to parse checkout response: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse checkout response: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
            }
        }
    }

    private suspend fun closeHyperInstance() {
        try {
            ensureReady()
            val requestId = UUID.randomUUID().toString()
            logger(LogType.DEBUG, EventName.CLOSE_HYPER_INSTANCE, "")
            val jsCode = ClickToPayScripts.closeHyperInstance(requestId)
            val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.CLOSE_HYPER_INSTANCE_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.CLOSE_HYPER_INSTANCE_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
            }
            logger(LogType.DEBUG, EventName.CLOSE_HYPER_INSTANCE_RETURNED, "")
        } catch (e: Exception) {
            logger(
                LogType.ERROR, EventName.CLOSE_HYPER_INSTANCE_RETURNED, "message: ${e.message}"
            )
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
            logger(LogType.DEBUG, EventName.CLOSE_INIT, "")
            closeHyperInstance()
            webViewManager.cancelPendingRequests("session is being closed")
            restoreAccessibility()
            webViewManager.destroy()
            isDestroyed.set(true)

            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            logger(LogType.DEBUG, EventName.CLOSE_RETURNED, "")
        } catch (e: Exception) {
            logger(
                LogType.ERROR,
                EventName.CLOSE_RETURNED,
                "Message: ${e.message}",
                LogCategory.USER_ERROR
            )
            throw ClickToPayException(
                "Failed to close Click to Pay session: ${e.message}", "CLOSE_ERROR"
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
        logger(LogType.DEBUG, EventName.SIGN_OUT_INIT, "")
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode = ClickToPayScripts.signOut(requestId)
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = when (val result = ClickToPayResponseDecoder.parseJSONObject(responseJson)) {
                is DecodeResult.Success -> result.data
                is DecodeResult.Error -> {
                    logger(LogType.ERROR, EventName.SIGN_OUT_RETURNED, result.message, LogCategory.USER_ERROR)
                    throw ClickToPayException(result.message, "ERROR")
                }
            }
            val data = ClickToPayResponseDecoder.getNestedObject(jsonObject, "data")
            ClickToPayResponseDecoder.decodeError(data)?.let { (errorType, errorMessage) ->
                logger(LogType.ERROR, EventName.SIGN_OUT_RETURNED, "Type: $errorType, Message: $errorMessage", LogCategory.USER_ERROR)
                webViewManager.cancelPendingRequests()
                webViewManager.detach()
                throw ClickToPayException("Failed to SignOut: $errorMessage", errorType)
            }
            when (val result = ClickToPayResponseDecoder.decodeSignOutResponse(data)) {
                is DecodeResult.Success -> {
                    logger(
                        LogType.DEBUG,
                        EventName.SIGN_OUT_RETURNED,
                        "recognized: ${result.data.recognized}"
                    )
                    result.data
                }
                is DecodeResult.Error -> {
                    logger(
                        LogType.ERROR,
                        EventName.SIGN_OUT_RETURNED,
                        "Failed to parse sign out response: ${result.message}",
                        LogCategory.USER_ERROR
                    )
                    throw ClickToPayException(
                        "Failed to parse sign out response: ${result.message}",
                        "PARSE_ERROR"
                    )
                }
            }
        }
    }
}
