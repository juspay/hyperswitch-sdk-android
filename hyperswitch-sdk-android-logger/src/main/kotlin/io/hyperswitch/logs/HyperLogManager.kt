package io.hyperswitch.logs

import io.hyperswitch.networking.HyperNetworking
import org.json.JSONArray

/**
 * Singleton manager for handling logging operations in Hyperswitch SDK.
 * Manages log batching, debouncing, and sending logs to the logging endpoint.
 */
object HyperLogManager {

    private val logsBatch = mutableListOf<HSLog>()
    private var publishableKey: String? = null
    private var loggingEndPoint: String? = null
    private const val DEFAULT_DELAY_IN_MILLIS = 2000L
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
        delayInMillis = delay
        this.publishableKey = publishableKey
        this.loggingEndPoint = loggingEndPoint
    }

    /**
     * Adds a log to the batch and triggers debounced log sending.
     *
     * @param log The log entry to be added to the batch
     */
    fun addLog(log: HSLog) {
        logsBatch.add(log)
        debouncedPushLogs()
    }

    /**
     * Updates the logging endpoint URL.
     *
     * @param customLoggingUrl The new custom logging endpoint URL
     */
    fun setLoggingEndPoint(customLoggingUrl: String) {
        this.loggingEndPoint = customLoggingUrl
    }

    /**
     * Reads logs from file storage and sends them to the logging endpoint.
     * Clears the file after successful transmission.
     *
     * @param fileManager The file manager instance to read logs from
     */
    fun sendLogsFromFile(fileManager: LogFileManager) {
        if (publishableKey.isNullOrBlank()) return
        try {
            val logArray = fileManager.getAllLogs()
            if (logArray.length() > 0) {
                enrichLogs(logArray)
                loggingEndPoint?.let { endpoint ->
                    try {
                        HyperNetworking.makePostRequest(
                            endpoint,
                            logArray.toString(),
                            callback = { result ->
                                result.onSuccess {
                                    fileManager.clearFile()
                                }
                            })
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Sets the OTA (Over-The-Air) version for log enrichment.
     * Triggers debounced log sending after updating the version.
     *
     * @param version The OTA version string to be included in logs
     */
    fun setOtaVersion(version: String) {
        hyperOtaVersion = version
        debouncedPushLogs()
    }

    private fun debouncedPushLogs() {
        debouncer.debounce { sendLogsOverNetwork() }
    }

    private fun enrichLogs(logsArray: JSONArray) {
        for (i in 0 until logsArray.length()) {
            logsArray.getJSONObject(i).apply {
                put("merchant_id", publishableKey)
                put("code_push_version", hyperOtaVersion)
                put("client_core_version", hyperOtaVersion)
            }
        }
    }

    /**
     * Converts all logs in the current batch to a JSON array string.
     *
     * @return A JSON array string representation of all logs in the batch
     */
    fun getAllLogsAsString(): String = logsBatch.joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun sendLogsOverNetwork() {
        if (logsBatch.isEmpty()){
            return
        }
        if (!publishableKey.isNullOrBlank() && !loggingEndPoint.isNullOrBlank()) {
            logsBatch.map { log ->
                log.apply {
                    merchantId = publishableKey!!
                    codePushVersion = hyperOtaVersion
                    clientCoreVersion = hyperOtaVersion
                }
            }
            val logsToSend = getAllLogsAsString()
            logsBatch.clear()
            loggingEndPoint?.let { endpoint ->
                try {
                    HyperNetworking.makePostRequest(
                        endpoint, logsToSend,
                        callback = {}
                    )
                } catch (_: Exception) {
                }
            }
        } else {
            debouncedPushLogs()
        }
    }
}
