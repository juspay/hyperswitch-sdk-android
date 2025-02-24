package io.hyperswitch.logs

import android.content.Context
import io.hyperswitch.networking.HyperNetworking
import io.hyperswitch.react.Utils.Companion.getLoggingUrl
import org.json.JSONArray

object HyperLogManager {

    private val logsBatch = mutableListOf<Log>()
    private var publishableKey: String? = null
    private var loggingEndPoint: String? = null
    private const val DEFAULT_DELAY_IN_MILLIS = 2000L
    private var delayInMillis: Long = DEFAULT_DELAY_IN_MILLIS
    private var hyperOtaVersion: String = ""
    fun sendLogsFromFile(fileManager: LogFileManager) {
        try {
            publishableKey?.let {
                val logArray = fileManager.getAllLogs()
                if (logArray.length() > 0) {
                    addValuesToLogs(logArray)
                    loggingEndPoint?.let { endpoint ->
                        HyperNetworking.makePostRequest(endpoint, logArray.toString())
                        fileManager.clearFile()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setOtaVersion(hyperOtaVersion: String) {
        this.hyperOtaVersion = hyperOtaVersion
        debouncedPushLogs()
    }

    private fun addValuesToLogs(logsArray: JSONArray) {
        for (i in 0 until logsArray.length()) {
            logsArray.getJSONObject(i).put("merchant_id", publishableKey)
            logsArray.getJSONObject(i).put("code_push_version", hyperOtaVersion)
            logsArray.getJSONObject(i).put("client_core_version", hyperOtaVersion.split('-')[0])
        }
    }

    private fun debouncedPushLogs() {
        Debouncer(delayInMillis).debounce { sendLogsOverNetwork() }
    }

    fun initialise(
            context: Context,
            publishableKey: String,
            delayInMillis: Long = DEFAULT_DELAY_IN_MILLIS
    ) {
        this.publishableKey = publishableKey
        this.delayInMillis = delayInMillis
        loggingEndPoint = getLoggingUrl(publishableKey)
    }

    fun addLog(log: Log) {
        logsBatch.add(log)
        debouncedPushLogs()
    }

    fun getAllLogsAsString(): String {
        return logsBatch.joinToString(prefix = "[", postfix = "]") { it.toJson() }
    }

    private fun addValuesToLogs() {
        logsBatch.forEach { log ->
            publishableKey?.let {
                log.merchantId = it
                log.codePushVersion = hyperOtaVersion
                log.clientCoreVersion = hyperOtaVersion.split('-')[0]
            }
        }
    }

    private fun sendLogsOverNetwork() {
        if (logsBatch.isNotEmpty()) {
            publishableKey?.let {
                addValuesToLogs()
                val logsToSend = logsBatch.joinToString(prefix = "[", postfix = "]") { it.toJson() }
                logsBatch.clear()
                loggingEndPoint?.let { HyperNetworking.makePostRequest(it, logsToSend) }
            }
        }
    }
}
