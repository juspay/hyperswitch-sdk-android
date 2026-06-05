package io.hyperswitch.click_to_pay.webview

import android.app.Activity
import android.view.ViewGroup
import io.hyperswitch.click_to_pay.models.*
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.getOptJSONArray
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.getOptJSONObject
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseCheckoutResponse
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseJSONObject
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseMaskedValidationChannelData
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseRecognizedCard
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseSupportedValidationChannelsData
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.setLogger as setParserLogger
import io.hyperswitch.click_to_pay.utils.ClickToPayWebViewManager
import io.hyperswitch.click_to_pay.utils.HyperLoaderUtils
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class UCTPWebview(
    private val activity: Activity,
) {

    private val manager = ClickToPayWebViewManager(activity)
    private var logger: ((LogType, EventName, String, LogCategory) -> Unit)? = null

    fun setLogger(logger: (LogType, EventName, String, LogCategory) -> Unit) {
        this.logger = logger
        manager.setLogger(logger)
        setParserLogger(logger)
    }

    suspend fun evaluateJavascriptOnMainThread(requestId: String, jsCode: String): String {
        return manager.evaluateJavascriptOnMainThread(requestId, jsCode)
    }

    fun captureCorrelationIds(callback: (String) -> Unit) {
        manager.captureCorrelationIds(callback)
    }

    suspend fun ensureReady() {
        manager.ensureReady()
    }

    @Throws(ClickToPayException::class)
    suspend fun close(closeHyperInstance: Boolean) {
        manager.close(closeHyperInstance)
    }

    suspend fun moveToActivity(newActivity: Activity) {
        manager.moveToActivity(newActivity)
    }

    suspend fun loadSource(
        publishableKey: String,
        customBackendUrl: String?,
        customLogUrl: String?
    ) {
        ensureReady()
        val requestId: String = UUID.randomUUID().toString()
        val baseUrl = HyperLoaderUtils.Companion.getBaseUrl(publishableKey)
        val hyperLoaderUrl = HyperLoaderUtils.Companion.getHyperLoaderURL(publishableKey)
        logger?.invoke(
            LogType.DEBUG,
            EventName.SCRIPT_LOAD_INIT,
            "hyperLoaderUrl: $hyperLoaderUrl, baseUrl: $baseUrl",
            LogCategory.USER_EVENT
        )
        val baseHtml =
            "<!DOCTYPE html><html><head><script>function handleScriptError(){console.error('ClickToPay','Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='${hyperLoaderUrl}' onload='initHyper()' onerror='handleScriptError()' async></script></head><body></body></html>"
        val responseJson = manager.loadSource(baseHtml, baseUrl, requestId)
        withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.SCRIPT_LOAD_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.SCRIPT_LOAD_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to load URL - Type: $errorType, Message: $errorMessage",
                    "SCRIPT_LOAD_ERROR"
                )
            }
            logger?.invoke(
                LogType.DEBUG, EventName.SCRIPT_LOAD_RETURNED, "success", LogCategory.USER_EVENT
            )
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        request3DSAuthentication: Boolean
    ) {
        logger?.invoke(
            LogType.DEBUG,
            EventName.INIT_CLICK_TO_PAY_SESSION_INIT,
            "request3DSAuthentication: $request3DSAuthentication",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const authenticationSession=window.hyperInstance.initAuthenticationSession({clientSecret:'$clientSecret',profileId:'$profileId',authenticationId:'$authenticationId',merchantId:'$merchantId'});window.ClickToPaySession=await authenticationSession.initClickToPaySession({request3DSAuthentication:$request3DSAuthentication});const data=window.ClickToPaySession.error?window.ClickToPaySession:{success:true};window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:data}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'InitClickToPaySessionError',message:error.message}}}))}})();"
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject =
                parseJSONObject(responseJson, EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to initialize Click to Pay session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_SESSION_ERROR"
                )
            }
            logger?.invoke(
                LogType.DEBUG,
                EventName.INIT_CLICK_TO_PAY_SESSION_RETURNED,
                "",
                LogCategory.USER_EVENT
            )
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun getUserType(): CardsStatusResponse {
        logger?.invoke(LogType.DEBUG, EventName.GET_USER_TYPE_INIT, "", LogCategory.USER_EVENT)
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const userType=await window.ClickToPaySession.getUserType();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:userType}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.GET_USER_TYPE_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown Error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.GET_USER_TYPE_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    message = "Failed to get user type : $errorMessage", errorType
                )
            }
            val statusCodeStr = data.optString("statusCode", "NO_CARDS_PRESENT").uppercase()
            val maskedValidationChannelDetails =
                parseMaskedValidationChannelData(getOptJSONObject(data, "maskedValidationChannel"))
            val supportedValidationChannelsArray = data.optJSONArray("supportedValidationChannels")
            val supportedValidationChannels = supportedValidationChannelsArray?.let { array ->
                (0 until array.length()).map { i ->
                    parseSupportedValidationChannelsData(getOptJSONArray(array, i))
                }
            } ?: emptyList()

            logger?.invoke(
                LogType.DEBUG,
                EventName.GET_USER_TYPE_RETURNED,
                "statusCode: $statusCodeStr, maskedValidationChannels: $maskedValidationChannelDetails",
                LogCategory.USER_EVENT
            )

            CardsStatusResponse(
                statusCode = StatusCode.from(statusCodeStr),
                maskedValidationChannel = maskedValidationChannelDetails,
                supportedValidationChannels = supportedValidationChannels
            )
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun getRecognizedCards(): List<RecognizedCard> {
        logger?.invoke(
            LogType.DEBUG,
            EventName.GET_RECOGNISED_CARDS_INIT,
            "",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.getRecognizedCards();window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'GetRecognizedCardsError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.GET_RECOGNISED_CARDS_RETURNED)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = getOptJSONObject(data, "error")
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.GET_RECOGNISED_CARDS_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    "Failed to get recognized cards - Type: $errorType, Message: $errorMessage",
                    errorType
                )
            }
            val cardsArray = data as JSONArray
            val cards = (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(getOptJSONArray(cardsArray, i))
            }
            val visaCount = cards.count { it.paymentCardDescriptor == CardType.VISA }
            val masterCardCount = cards.count { it.paymentCardDescriptor == CardType.MASTERCARD }
            logger?.invoke(
                LogType.DEBUG,
                EventName.GET_RECOGNISED_CARDS_RETURNED,
                "Visa: $visaCount, Mastercard: $masterCardCount",
                LogCategory.USER_EVENT
            )
            cards
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun validateCustomerAuthentication(otpValue: String): List<RecognizedCard> {
        logger?.invoke(
            LogType.DEBUG,
            EventName.VALIDATE_CUSTOMER_AUTHENTICATION_INIT,
            "",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const cards=await window.ClickToPaySession.validateCustomerAuthentication({value:'$otpValue'});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:cards}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:error.type||'ERROR',message:error.message}}}))}})();"
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        return withContext(Dispatchers.Default) {
            val jsonObject =
                parseJSONObject(responseJson, EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED)
            val data = jsonObject.get("data")

            if (data is JSONObject && data.has("error")) {
                val error = getOptJSONObject(data, "error")
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(
                    errorMessage, errorType
                )
            }
            val cardsArray = data as JSONArray
            val cards = (0 until cardsArray.length()).map { i ->
                parseRecognizedCard(getOptJSONArray(cardsArray, i))
            }
            val visaCount = cards.count { it.paymentCardDescriptor == CardType.VISA }
            val masterCardCount = cards.count { it.paymentCardDescriptor == CardType.MASTERCARD }
            logger?.invoke(
                LogType.DEBUG,
                EventName.VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED,
                "Visa: $visaCount, Mastercard: $masterCardCount",
                LogCategory.USER_EVENT
            )
            cards
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun checkoutWithCard(request: CheckoutRequest): CheckoutResponse {
        logger?.invoke(
            LogType.DEBUG,
            EventName.CHECKOUT_INIT,
            "rememberMe: ${request.rememberMe}",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        manager.setModalAccessibilityForWebView(rootView)
        val requestId = UUID.randomUUID().toString()
        logger?.invoke(
            LogType.DEBUG, EventName.CREATE_NEW_WEBVIEW_INIT, "", LogCategory.USER_EVENT
        )
        val jsCode =
            "(async function(){try{const checkoutResponse=await window.ClickToPaySession.checkoutWithCard({srcDigitalCardId:'${request.srcDigitalCardId}',rememberMe:${request.rememberMe}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:checkoutResponse}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'CheckoutWithCardError',message:error.message}}}))}})();"
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)
        logger?.invoke(
            LogType.DEBUG,
            EventName.CREATE_NEW_WEBVIEW_RETURNED,
            "",
            LogCategory.USER_EVENT
        )
        manager.restoreAccessibility()
        return withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.CHECKOUT_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.CHECKOUT_RETURNED,
                    "Type: $errorType, Message: $errorMessage, error: $error",
                    LogCategory.USER_ERROR
                )
                throw ClickToPayException(errorMessage, errorType)
            }
            parseCheckoutResponse(data).also {
                logger?.invoke(
                    LogType.DEBUG,
                    EventName.CHECKOUT_RETURNED,
                    "status: ${it.status}",
                    LogCategory.USER_EVENT
                )
            }
        }
    }
}
