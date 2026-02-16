package io.hyperswitch.logs

import io.hyperswitch.networking.HyperNetworking
import org.json.JSONArray

/**
 * Singleton manager for handling logging operations in Hyperswitch SDK.
 * Manages log batching, debouncing, and sending logs to the logging endpoint.
 */
object HyperLogManager {

    private val lock = Any()
    private val logsBatch = mutableListOf<HSLog>()
    private var publishableKey: String? = null
    private var loggingEndPoint: String? = null
    private const val DEFAULT_DELAY_IN_MILLIS = 2000L
    private const val BUCKET_SIZE = 8

    private var delayInMillis: Long = DEFAULT_DELAY_IN_MILLIS
    private var hyperOtaVersion: String = ""
    private val debouncer = Debouncer(DEFAULT_DELAY_IN_MILLIS)

    /**
     * Initializes the log manager with required configuration.
     *
     * @param publishableKey The merchant's publishable key for authentication
     * @param loggingEndPoint The endpoint URL where logs will be sent
     * @param delay The debounce delay in milliseconds before sending logs (default: 2000ms)
     */
    fun initialise(
        publishableKey: String,
        loggingEndPoint: String,
        delay: Long = DEFAULT_DELAY_IN_MILLIS
    ) {
        synchronized(lock) {
            this.publishableKey = publishableKey
            this.loggingEndPoint = loggingEndPoint
            this.delayInMillis = delay
        }
    }
    /**
     * Updates the logging endpoint URL.
     *
     * @param customLoggingUrl The new custom logging endpoint URL
     */
    fun setLoggingEndPoint(customLoggingUrl: String) {
        synchronized(lock) {
            loggingEndPoint = customLoggingUrl
        }
    }

    /**
     * Sets the OTA (Over-The-Air) version for log enrichment.
     * Triggers debounced log sending after updating the version.
     *
     * @param version The OTA version string to be included in logs
     */
    fun setOtaVersion(version: String) {
        synchronized(lock) {
            hyperOtaVersion = version
        }
        debouncedPushLogs()
    }

    /**
     * Adds a log to the batch and triggers debounced log sending.
     *
     * @param log The log entry to be added to the batch
     */
    fun addLog(log: HSLog) {
        val shouldSendNow: Boolean

        synchronized(lock) {
            logsBatch.add(log)
            shouldSendNow = logsBatch.size >= BUCKET_SIZE
        }

        if (shouldSendNow) {
            sendLogsOverNetwork()
        } else {
            debouncedPushLogs()
        }
    }

    private fun debouncedPushLogs() {
        debouncer.debounce { sendLogsOverNetwork() }
    }

     private fun enrichLogs(
        logsArray: JSONArray,
        merchantId: String,
        otaVersion: String
    ) {
        for (i in 0 until logsArray.length()) {
            logsArray.getJSONObject(i).apply {
                put("merchant_id", merchantId)
                put("code_push_version", otaVersion)
                put("client_core_version", otaVersion)
            }
        }
    }

    private fun sendLogsOverNetwork() {
        val logsToSend: String
        val endpoint: String
        val merchantId: String
        val otaVersion: String

        synchronized(lock) {
            if (logsBatch.isEmpty()) return
            if (publishableKey.isNullOrBlank() || loggingEndPoint.isNullOrBlank()) {
                debouncedPushLogs()
                return
            }

            merchantId = publishableKey!!
            endpoint = loggingEndPoint!!
            otaVersion = hyperOtaVersion

            // create safe snapshot
            logsBatch.forEach {
                it.merchantId = merchantId
                it.codePushVersion = otaVersion
                it.clientCoreVersion = otaVersion
            }

            logsToSend = logsBatch.joinToString(prefix = "[", postfix = "]") { it.toJson() }
            logsBatch.clear()
        }

        try {
            HyperNetworking.makePostRequest(endpoint, logsToSend) {}
        } catch (_: Exception) {
            // optional retry logic here
        }
    }

    // ---------- file logs ----------

    fun sendLogsFromFile(fileManager: LogFileManager) {
        val endpoint: String
        val merchantId: String
        val otaVersion: String

        synchronized(lock) {
            if (publishableKey.isNullOrBlank() || loggingEndPoint.isNullOrBlank()) return
            endpoint = loggingEndPoint!!
            merchantId = publishableKey!!
            otaVersion = hyperOtaVersion
        }

        try {
            val logArray = fileManager.getAllLogs()
            if (logArray.length() == 0) return

            HyperNetworking.makePostRequest(
                endpoint,
                logArray.toString()
            ) { result ->
                result.onSuccess { fileManager.clearFile() }
            }

        } catch (_: Exception) {
        }
    }
    fun getAllLogsAsString(): String {
        val snapshot = synchronized(lock) { logsBatch.toList() }
        return snapshot.joinToString(prefix = "[", postfix = "]") { it.toJson() }
    }

}
