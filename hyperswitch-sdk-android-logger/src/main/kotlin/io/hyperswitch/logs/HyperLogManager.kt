package io.hyperswitch.logs

import io.hyperswitch.networking.HyperNetworking
import org.json.JSONArray

object HyperLogManager {

    private val logsBatch = mutableListOf<HSLog>()
    private var publishableKey: String? = null
    private var loggingEndPoint: String? = null
    private const val DEFAULT_DELAY_IN_MILLIS = 2000L
    private var delayInMillis: Long = DEFAULT_DELAY_IN_MILLIS
    private var hyperOtaVersion: String = ""
    private val debouncer = Debouncer(DEFAULT_DELAY_IN_MILLIS)

    fun initialise(
        publishableKey: String,
        loggingEndPoint: String,
        delay: Long = DEFAULT_DELAY_IN_MILLIS
    ) {
        delayInMillis = delay
        this.publishableKey = publishableKey
        this.loggingEndPoint = loggingEndPoint
    }

    fun addLog(log: HSLog) {
        logsBatch.add(log)
        debouncedPushLogs()
    }

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
