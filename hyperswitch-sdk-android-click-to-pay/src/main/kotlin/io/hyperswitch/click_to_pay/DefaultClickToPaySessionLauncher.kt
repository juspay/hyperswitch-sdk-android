package io.hyperswitch.click_to_pay

import android.app.Activity
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.getOptJSONObject
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseJSONObject
import io.hyperswitch.click_to_pay.webview.DCTPWebview
import io.hyperswitch.click_to_pay.webview.UCTPWebview
import io.hyperswitch.logs.CrashHandler
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogFileManager
import io.hyperswitch.logs.LogType
import io.hyperswitch.logs.LogUtils.getLoggingUrl
import io.hyperswitch.logs.LogUtils.getOrCreateUniqueKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default implementation of ClickToPaySessionLauncher.
 *
 * Manages Click to Pay session lifecycle using WebView-based JavaScript bridge.
 * Delegates UCTP and DCTP operations to their respective webview handlers.
 *
 * @property activity The Android activity context
 * @property publishableKey The publishable API key for authentication
 * @property customBackendUrl Optional custom backend URL for API calls
 * @property customLogUrl Optional custom URL for logging
 */
class DefaultClickToPaySessionLauncher(
    private var activity: Activity,
    private val publishableKey: String,
    private val customBackendUrl: String? = null,
    private val customLogUrl: String? = null,
) : ClickToPaySessionLauncher {
    private val uctpWebview = UCTPWebview(activity)
    private val dctpWebview = DCTPWebview(activity)
    private val correlationIds = mutableSetOf<String>()
    private val captureCorrelationIds = AtomicBoolean(true)
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
                .version(BuildConfig.VERSION_NAME).authenticationId(authenticationId ?: "")
                .sessionId(sessionId)
        HyperLogManager.addLog(log.build())
    }

    init {
        uctpWebview.setLogger(::logger)
        dctpWebview.setLogger(::logger)
    }

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
        loadUrl()
    }

    private suspend fun loadUrl() {
        uctpWebview.loadSource(publishableKey, customBackendUrl, customLogUrl)
        dctpWebview.loadSource(publishableKey, customBackendUrl, customLogUrl)
    }

    @Throws(ClickToPayException::class)
    override suspend fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        request3DSAuthentication: Boolean
    ) {
        this.authenticationId = authenticationId
        this.sessionId = "${deviceUniqueSessionId}_${UUID.randomUUID()}"
        captureCorrelationIds.set(true)
        uctpWebview.captureCorrelationIds {
            if (it != "" && captureCorrelationIds.get()) {
                correlationIds.add(it)
            }
        }
        val token = uctpWebview.initClickToPaySession(
            clientSecret,
            profileId,
            authenticationId,
            merchantId,
            request3DSAuthentication
        ) ?: ""

        if (token.isNotEmpty()) {
            dctpWebview.initClickToPayDCTPSession(
                clientSecret,
                profileId,
                authenticationId,
                merchantId,
                token
            )
        }

        logger(
            LogType.DEBUG,
            EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED,
            correlationIds.joinToString(", "),
            LogCategory.USER_EVENT
        )
        captureCorrelationIds.set(false)
        correlationIds.clear()
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
        try {
            if (this.activity !== activity) {
                uctpWebview.moveToActivity(activity)
                dctpWebview.moveToActivity(activity)
                uctpWebview.setLogger { type, eventName, value, category ->
                    val log =
                        HSLog.LogBuilder().logType(type).category(category).eventName(eventName)
                            .value(value)
                            .version(BuildConfig.VERSION_NAME).authenticationId(authenticationId)
                            .sessionId(sessionId)
                    HyperLogManager.addLog(log.build())
                }
                dctpWebview.setLogger { type, eventName, value, category ->
                    val log =
                        HSLog.LogBuilder().logType(type).category(category).eventName(eventName)
                            .value(value)
                            .version(BuildConfig.VERSION_NAME).authenticationId(authenticationId)
                            .sessionId(sessionId)
                    HyperLogManager.addLog(log.build())
                }
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
        val jsCode =
            "(async function(){ try {let authenticationSession=window.hyperInstance.initAuthenticationSession({clientSecret:'$clientSecret',profileId:'$profileId',authenticationId:'$authenticationId',merchantId:'$merchantId'}); window.ClickToPaySession = await authenticationSession?.getActiveClickToPaySession();const data=window.ClickToPaySession.error?window.ClickToPaySession:{success:true};window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:data}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'getActiveClickToPaySessionError',message:error.message}}}))}})();"

        val responseJson = uctpWebview.evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject =
                parseJSONObject(responseJson, EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logger(
                    LogType.ERROR,
                    EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED,
                    "Failed to get Click to Pay session - Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to get Click to Pay session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_SESSION_ERROR"
                )
            }
            logger(
                LogType.DEBUG, EventName.GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED, ""
            )
        }
    }

    @Throws(ClickToPayException::class)
    override suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse {
        return dctpWebview.isCustomerPresent(request)
    }

    @Throws(ClickToPayException::class)
    override suspend fun getUserType(): CardsStatusResponse {
        return uctpWebview.getUserType()
    }

    @Throws(ClickToPayException::class)
    override suspend fun getRecognizedCards(): List<RecognizedCard> {
        return uctpWebview.getRecognizedCards()
    }

    @Throws(ClickToPayException::class)
    override suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard> {
        return uctpWebview.validateCustomerAuthentication(otpValue)
    }

    @Throws(ClickToPayException::class)
    override suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        return uctpWebview.checkoutWithCard(request)
    }

    override suspend fun signOut(): SignOutResponse {
        logger(LogType.DEBUG, EventName.SIGN_OUT_INIT, "")
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const signOutResponse = await window.ClickToPaySession.signOut();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data: signOutResponse }));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'SignOutError',message:error.message}}}))}})();"

        val responseJson = uctpWebview.evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.SIGN_OUT_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorMessage = error.optString("message", "SignOut Error")
                val errorType = error.optString("type", "SignOutError")
                logger(
                    LogType.ERROR,
                    EventName.SIGN_OUT_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to SignOut : $errorMessage", errorType
                )
            }
            val recognized = data.optBoolean("recognized", false)
            logger(
                LogType.DEBUG, EventName.SIGN_OUT_RETURNED, "recognized: $recognized"
            )
            SignOutResponse(
                recognized = recognized
            )
        }
    }

    @Throws(ClickToPayException::class)
    override suspend fun close(closeHyperInstance: Boolean) {
        try {
            uctpWebview.close(closeHyperInstance)
            dctpWebview.close(closeHyperInstance)
        } catch (e: Exception) {
            throw ClickToPayException(
                "Failed to close Click to Pay session: ${e.message}", "CLOSE_ERROR"
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
