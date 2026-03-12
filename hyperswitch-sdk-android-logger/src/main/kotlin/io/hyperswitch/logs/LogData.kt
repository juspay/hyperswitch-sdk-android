package io.hyperswitch.logs

import org.json.JSONException
import org.json.JSONObject

enum class LogType {
    DEBUG, INFO, ERROR, WARNING
}

enum class LogCategory {
    API, USER_ERROR, USER_EVENT, MERCHANT_EVENT, OTA_LIFE_CYCLE, AIRBORNE_LIFE_CYCLE
}

enum class EventName {
    AIRBORNE_INIT, AIRBORNE_FINISH, AIRBORNE_BOOT, AIRBORNE_EVENT, CRASH_EVENT,
    AUTHENTICATION_SESSION,
    AUTHENTICATION_SESSION_INIT,
    AUTHENTICATION_SESSION_RETURNED,

    INIT_CLICK_TO_PAY_SESSION,
    INIT_CLICK_TO_PAY_SESSION_INIT,
    CREATE_WEBVIEW_INIT,
    CREATE_WEBVIEW_RETURNED,
    INIT_CLICK_TO_PAY_SESSION_WEB_INIT,
    INIT_CLICK_TO_PAY_SESSION_WEB_RETURNED,
    INIT_CLICK_TO_PAY_SESSION_RETURNED,

    GET_ACTIVE_CLICK_TO_PAY_SESSION,
    GET_ACTIVE_CLICK_TO_PAY_SESSION_INIT,
    GET_ACTIVE_CLICK_TO_PAY_SESSION_WEB_INIT,
    GET_ACTIVE_CLICK_TO_PAY_SESSION_WEB_RETURNED,
    GET_ACTIVE_CLICK_TO_PAY_SESSION_RETURNED,


    IS_CUSTOMER_PRESENT,
    IS_CUSTOMER_PRESENT_INIT,
    IS_CUSTOMER_PRESENT_WEB_INIT,
    IS_CUSTOMER_PRESENT_WEB_RETURNED,
    IS_CUSTOMER_PRESENT_RETURNED,

    GET_USER_TYPE,
    GET_USER_TYPE_INIT,
    GET_USER_TYPE_WEB_INIT,
    GET_USER_TYPE_WEB_RETURNED,
    GET_USER_TYPE_RETURNED,

    GET_RECOGNISED_CARDS,
    GET_RECOGNISED_CARDS_INIT,
    GET_RECOGNISED_CARDS_WEB_INIT,
    GET_RECOGNISED_CARDS_WEB_RETURNED,
    GET_RECOGNISED_CARDS_RETURNED,


    VALIDATE_CUSTOMER_AUTHENTICATION,
    VALIDATE_CUSTOMER_AUTHENTICATION_INIT,
    VALIDATE_CUSTOMER_AUTHENTICATION_WEB_INIT,
    VALIDATE_CUSTOMER_AUTHENTICATION_WEB_RETURNED,
    VALIDATE_CUSTOMER_AUTHENTICATION_RETURNED,


    CHECKOUT,
    CHECKOUT_INIT,
    CHECKOUT_WEB_INIT,
    CREATE_NEW_WEBVIEW_INIT,
    CREATE_NEW_WEBVIEW_RETURNED,
    CHECKOUT_WEB_RETURNED,
    CLOSE_NEW_WEBVIEW,
    CHECKOUT_RETURNED,

    SIGN_OUT,
    SIGN_OUT_INIT,
    SIGN_OUT_WEB_INIT,
    SIGN_OUT_WEB_RETURNED,
    SIGN_OUT_RETURNED,

    CLOSE,
    CLOSE_INIT,
    CLOSE_WEBVIEW_INIT,
    CLOSE_WEBVIEW_RETURNED,
    CLOSE_RETURNED,

    WEBVIEW
}

data class HSLog(
    val timestamp: String,
    val logType: LogType,
    val component: String = "MOBILE",
    val category: LogCategory,
    val version: String,
    var codePushVersion: String,
    var clientCoreVersion: String,
    val value: String,
    val internalMetadata: String,
    val sessionId: String,
    val authenticationId: String,
    var merchantId: String,
    val paymentId: String,
    val appId: String? = null,
    val platform: String = "ANDROID",
    val userAgent: String,
    val eventName: EventName,
    val latency: String? = null,
    val firstEvent: Boolean = false,
    val paymentMethod: String? = null,
    val paymentExperience: String? = null,
    val source: String
) {
    fun toJson(): String {
        try {

            val logData = JSONObject().apply {
                put("timestamp", timestamp)
                put("log_type", logType.name)
                put("component", component)
                put("category", category.toString())
                put("version", version)
                put("code_push_version", codePushVersion)
                put("client_core_version", clientCoreVersion)
                put("value", value)
                put("internal_metadata", internalMetadata)
                put("session_id", sessionId)
                put("authentication_id", authenticationId)
                put("merchant_id", merchantId)
                put("payment_id", paymentId)
                put("app_id", appId ?: "")
                put("platform", platform)
                put("user_agent", userAgent)
                put("event_name", eventName.name)
                put("first_event", firstEvent.toString())
                put("payment_method", paymentMethod ?: "")
                put("payment_experience", paymentExperience ?: "")
                put("latency", latency ?: "")
                put("source", source)
            }

            val jsonString = logData.toString()
            return jsonString
        } catch (e: JSONException) {
            return ""
        }
    }


    class LogBuilder {
        private var timestamp: String = System.currentTimeMillis().toString()
        private var logType: LogType = LogType.INFO
        private var component: String = "MOBILE"
        private lateinit var category: LogCategory
        private var version: String = ""
        private var codePushVersion: String = ""
        private var clientCoreVersion: String = ""
        private var value: String = ""
        private var internalMetadata: String = ""
        private var sessionId: String = ""
        private var authenticationId: String = ""
        private var merchantId: String = ""
        private var paymentId: String = ""
        private var appId: String? = null
        private var platform: String = "ANDROID"
        private var userAgent: String = ""
        private lateinit var eventName: EventName
        private var latency: String? = null
        private var firstEvent: Boolean = false
        private var paymentMethod: String? = null
        private var paymentExperience: String? = null
        private var source: String = ""

        fun timestamp(timestamp: String) = apply { this.timestamp = timestamp }
        fun logType(logType: LogType) = apply {
            this.logType = logType
        }

        fun logType(logType: String) = apply {
            this.logType =
                LogType.entries.find { it.name == logType.uppercase() } ?: LogType.INFO
        }

        fun component(component: String) = apply { this.component = component }
        fun category(category: LogCategory) = apply { this.category = category }
        fun version(version: String) = apply { this.version = version }
        fun codePushVersion(codePushVersion: String) =
            apply { this.codePushVersion = codePushVersion }

        fun clientCoreVersion(clientCoreVersion: String) =
            apply { this.clientCoreVersion = clientCoreVersion }

        fun value(value: String) = apply { this.value = value }
        fun internalMetadata(internalMetadata: String) =
            apply { this.internalMetadata = internalMetadata }

        fun sessionId(sessionId: String) = apply { this.sessionId = sessionId }
        fun authenticationId(authenticationId: String) =
            apply { this.authenticationId = authenticationId }

        fun merchantId(merchantId: String) = apply { this.merchantId = merchantId }
        fun paymentId(paymentId: String) = apply { this.paymentId = paymentId }
        fun appId(appId: String?) = apply { this.appId = appId }
        fun platform(platform: String) = apply { this.platform = platform }
        fun userAgent(userAgent: String) = apply { this.userAgent = userAgent }
        fun eventName(eventName: EventName) = apply { this.eventName = eventName }
        fun latency(latency: String?) = apply { this.latency = latency }
        fun firstEvent(firstEvent: Boolean) = apply { this.firstEvent = firstEvent }
        fun paymentMethod(paymentMethod: String?) = apply { this.paymentMethod = paymentMethod }
        fun paymentExperience(paymentExperience: String?) =
            apply { this.paymentExperience = paymentExperience }

        fun source(source: String) = apply { this.source = source }

        fun build(): HSLog {
            return HSLog(
                timestamp,
                logType,
                component,
                category,
                version,
                codePushVersion,
                clientCoreVersion,
                value,
                internalMetadata,
                sessionId,
                authenticationId,
                merchantId,
                paymentId,
                appId,
                platform,
                userAgent,
                eventName,
                latency,
                firstEvent,
                paymentMethod,
                paymentExperience,
                source
            )
        }
    }
}
