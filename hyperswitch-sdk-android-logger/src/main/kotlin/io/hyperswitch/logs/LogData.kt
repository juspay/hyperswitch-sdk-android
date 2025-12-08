package io.hyperswitch.logs

import org.json.JSONException
import org.json.JSONObject

enum class LogType {
    DEBUG, INFO, ERROR, WARNING
}

enum class LogCategory {
    API, USER_ERROR, USER_EVENT, MERCHANT_EVENT, OTA_LIFE_CYCLE
}

enum class EventName {
    HYPER_OTA_INIT, HYPER_OTA_FINISH , HYPER_OTA_EVENT, CRASH_EVENT, CLICK_TO_PAY_FLOW
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
