package io.hyperswitch.click_to_pay.webview

import android.app.Activity
import io.hyperswitch.click_to_pay.models.ClickToPayException
import io.hyperswitch.click_to_pay.models.CustomerPresenceRequest
import io.hyperswitch.click_to_pay.models.CustomerPresenceResponse
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.getOptJSONObject
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.parseJSONObject
import io.hyperswitch.click_to_pay.utils.ClickToPayModelParser.Companion.setLogger as setParserLogger
import io.hyperswitch.click_to_pay.utils.ClickToPayWebViewManager
import io.hyperswitch.click_to_pay.utils.HyperLoaderUtils
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.LogCategory
import io.hyperswitch.logs.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DCTPWebview(
    activity: Activity,
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
        val hyperLoaderUrl = HyperLoaderUtils.Companion.getHyperLoaderURL(publishableKey)
        val baseUrl = HyperLoaderUtils.Companion.getBaseUrl(publishableKey)
        logger?.invoke(
            LogType.DEBUG,
            EventName.DCTP_SCRIPT_LOAD_INIT,
            "hyperLoaderUrl: $hyperLoaderUrl, baseUrl: $baseUrl",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId: String = UUID.randomUUID().toString()
        val mastercardDirectURL = HyperLoaderUtils.Companion.getMasterCardDirectUrl(publishableKey)
        val visaDirectURL = HyperLoaderUtils.Companion.getVisaDirectUrl(publishableKey)
        val baseHtml =
            "<!DOCTYPE html><html><body><script>function handleScriptError(){console.error('ClickToPay','Failed to load HyperLoader.js');window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}}));}async function initHyper(){try{if(typeof Hyper==='undefined'){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}}));return;}window.hyperInstance=Hyper.init('$publishableKey',{${customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""}${customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{sdkInitialised:true}}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'HyperInitializationError',message:error.message}}}))}}</script><script src='$visaDirectURL'></script><script src='$mastercardDirectURL'></script><script src='${hyperLoaderUrl}' onload='initHyper()' onerror='handleScriptError()' async></script></body></html>"

        val responseJson = manager.loadSource(baseHtml, baseUrl, requestId)
        withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.DCTP_SCRIPT_LOAD_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.DCTP_SCRIPT_LOAD_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                manager.cancelPendingRequests()
                manager.detachWebView()
                throw ClickToPayException(
                    "Failed to load URL - Type: $errorType, Message: $errorMessage",
                    "SCRIPT_LOAD_ERROR"
                )
            }
            logger?.invoke(
                LogType.DEBUG,
                EventName.DCTP_SCRIPT_LOAD_RETURNED,
                "success",
                LogCategory.USER_EVENT
            )
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun initClickToPayDCTPSession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        token: String
    ) {
        logger?.invoke(
            LogType.DEBUG,
            EventName.INIT_CLICK_TO_PAY_DCTP_SESSION_INIT,
            "initClickToPayDCTPSession",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const authenticationSession=window.hyperInstance.initAuthenticationSession({clientSecret:'$clientSecret',profileId:'$profileId',authenticationId:'$authenticationId',merchantId:'$merchantId'});window.ClickToPaySession=await authenticationSession.initClickToPayDCTPSession({token:$token});const data=window.ClickToPaySession.error?window.ClickToPaySession:{success:true};window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:data}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'InitClickToPayDCTPSessionError',message:error.message}}}))}})();"
        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        withContext(Dispatchers.Default) {
            val jsonObject =
                parseJSONObject(responseJson, EventName.INIT_CLICK_TO_PAY_DCTP_SESSION_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "Unknown")
                val errorMessage = error.optString("message", "Unknown error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.INIT_CLICK_TO_PAY_DCTP_SESSION_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                manager.cancelPendingRequests()
                manager.detachWebView()
                throw ClickToPayException(
                    "Failed to initialize Click to Pay DCTP session - Type: $errorType, Message: $errorMessage",
                    "INIT_CLICK_TO_PAY_DCTP_SESSION_ERROR"
                )
            }
            logger?.invoke(
                LogType.DEBUG,
                EventName.INIT_CLICK_TO_PAY_DCTP_SESSION_RETURNED,
                "DCTP session initialized",
                LogCategory.USER_EVENT
            )
        }
    }

    @Throws(ClickToPayException::class)
    suspend fun isCustomerPresent(request: CustomerPresenceRequest): CustomerPresenceResponse {
        logger?.invoke(
            LogType.DEBUG,
            EventName.IS_CUSTOMER_PRESENT_INIT,
            "",
            LogCategory.USER_EVENT
        )
        ensureReady()
        val requestId = UUID.randomUUID().toString()
        val jsCode =
            "(async function(){try{const isCustomerPresent=await window.ClickToPaySession.isCustomerPresent({${request.email?.let { "email:'${request.email}'" } ?: ""}});window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:isCustomerPresent}));}catch(error){window.HSAndroidInterface.postMessage(JSON.stringify({requestId:'$requestId',data:{error:{type:'IsCustomerPresentError',message:error.message}}}))}})();"

        val responseJson = evaluateJavascriptOnMainThread(requestId, jsCode)

        return withContext(Dispatchers.Default) {
            val jsonObject = parseJSONObject(responseJson, EventName.IS_CUSTOMER_PRESENT_RETURNED)
            val data = getOptJSONObject(jsonObject, "data")
            val error = data.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "ERROR")
                val errorMessage = error.optString("message", "Unknown Error")
                logger?.invoke(
                    LogType.ERROR,
                    EventName.IS_CUSTOMER_PRESENT_RETURNED,
                    "Type: $errorType, Message: $errorMessage",
                    LogCategory.USER_ERROR
                )
                manager.cancelPendingRequests()
                manager.detachWebView()
                throw ClickToPayException(
                    "Failed to get customer present: $errorMessage", errorType
                )
            }
            val customerPresent = data.optBoolean("customerPresent", false)
            logger?.invoke(
                LogType.DEBUG,
                EventName.IS_CUSTOMER_PRESENT_RETURNED,
                "customerPresent: $customerPresent",
                LogCategory.USER_EVENT
            )
            CustomerPresenceResponse(
                customerPresent = customerPresent
            )
        }
    }
}
