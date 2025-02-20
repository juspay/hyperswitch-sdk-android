package io.hyperswitch.logs


enum class LogType {
    DEBUG, INFO, ERROR, WARNING
}

enum class LogCategory {
    API, USER_ERROR, USER_EVENT, MERCHANT_EVENT
}

enum class EventName {
    HYPER_OTA_INIT, RC_DOWNLOAD, BUNDLE_DOWNLOAD, BUNDLE_UNZIP_AND_VERIFY, BUNDLE_PATH_RETURN
}

data class Log(
    val timestamp: String,
    val logType: LogType,
    val component: String,
    val category: LogCategory,
    val version: String,
    val codePushVersion: String,
    val clientCoreVersion: String,
    val value: String,
    val internalMetadata: String,
    val sessionId: String,
    var merchantId: String,
    val paymentId: String,
    val appId: String? = null, // Optional field
    val platform: String,
    val userAgent: String,
    val eventName: EventName,
    val latency: String? = null, // Optional field
    val firstEvent: Boolean,
    val paymentMethod: String? = null, // Optional field
    val paymentExperience: String? = null, // Optional field
    val source: String
) {



    fun toJson(): String {
        return """
            {
                "timestamp": "$timestamp",
                "log_type": "${logType.name}",
                "component": "${component}",
                "category": "${category}",
                "version": "$version",
                "code_push_version": "$codePushVersion",
                "client_core_version": "$clientCoreVersion",
                "value": "$value",
                "internal_metadata": "{\"response\":null}",
                "session_id": "$sessionId",
                "merchant_id": "$merchantId",
                "payment_id": "$paymentId",
                "app_id": "${appId ?: ""}",
                "platform": "$platform",
                "user_agent": "$userAgent",
                "event_name": "${eventName.name}",
                "first_event": "$firstEvent",
                "payment_method": "${paymentMethod ?: ""}",
                "payment_experience": "${paymentExperience ?: ""}",
                "latency": "${latency ?: ""}",
                "source": "$source"
            }
        """.trimIndent().replace("\n", "").replace("\r", "") .replace("\\s".toRegex(), "")
    }

}




class LogBuilder {
    private var timestamp: String = ""
    private lateinit var logType: LogType
    private var component: String = "MOBILE"
    private lateinit var category: LogCategory
    private var version: String = ""
    private var codePushVersion: String = ""
    private var clientCoreVersion: String = ""
    private var value: String = ""
    private var internalMetadata: String = "{\"response\":null}"
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
    fun logType(logType: LogType) = apply { this.logType = logType }
    fun component(component: String) = apply { this.component = component }
    fun category(category: LogCategory) = apply { this.category = category }
    fun version(version: String) = apply { this.version = version }
    fun codePushVersion(codePushVersion: String) = apply { this.codePushVersion = codePushVersion }
    fun clientCoreVersion(clientCoreVersion: String) = apply { this.clientCoreVersion = clientCoreVersion }
    fun value(value: String) = apply { this.value = value }
    fun internalMetadata(internalMetadata: String) = apply { this.internalMetadata = internalMetadata }
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
    fun paymentExperience(paymentExperience: String?) = apply { this.paymentExperience = paymentExperience }
    fun source(source: String) = apply { this.source = source }

    fun build(): Log {
        timestamp=System.currentTimeMillis().toString()
        return Log(
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


