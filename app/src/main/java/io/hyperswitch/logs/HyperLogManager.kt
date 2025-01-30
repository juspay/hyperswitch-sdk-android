package io.hyperswitch.logs

import android.content.Context
import io.hyperswitch.networking.HyperNetworking
import io.hyperswitch.react.SDKEnvironment
import io.hyperswitch.react.Utils.Companion.checkEnvironment
import org.json.JSONArray

object HyperLogManager {

    private var logsBatch = ArrayList<Log>()
    private lateinit var publishableKey: String
    private var loggingEndPoint: String? = null

    fun sendLogsFromFile(fileManager: LogFileManager) {
        try {
            val logArray = fileManager.getAllLogs()
            appendPublishableKeyToJsonLog(logArray)
            if (logArray.length() > 0) {
                val logArrayString = logArray.toString()
                loggingEndPoint?.let { endpoint ->
                    HyperNetworking.makePostRequest(endpoint, logArrayString)
                    fileManager.clearFile()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun appendPublishableKeyToJsonLog(logsArray: JSONArray) {
        for (i in 0 until logsArray.length()) {
            val logObject = logsArray.getJSONObject(i)
            logObject.put("merchant_id", publishableKey)
        }
    }

    private fun debouncedPushLogs() {
        Debouncer(20000).debounce {
            sendLogsOverNetwork()
        }
    }

    fun initialise(context: Context, publishableKey: String) {
        this.publishableKey = publishableKey
        val env = checkEnvironment(publishableKey)
        loggingEndPoint = if (env == SDKEnvironment.PROD) "https://api.hyperswitch.io/logs/sdk"
        else "https://sandbox.hyperswitch.io/logs/sdk"
    }

    fun addLog(log: Log) {
        logsBatch.add(log)
        debouncedPushLogs()
    }

    private fun getStringifiedLogs(logBatch: ArrayList<Log>): ArrayList<String> {
        return ArrayList(logBatch.map { it.toJson() })
    }

    fun getAllLogsAsString(): String {
        return getStringifiedLogs(logsBatch).toString()
    }

    private fun addPublishableKeyToLogs() {
        logsBatch.forEach { log ->
            log.merchantId = this.publishableKey
        }
    }

    private fun sendLogsOverNetwork() {
        addPublishableKeyToLogs()
        val logsToSend: ArrayList<String> = getStringifiedLogs(logsBatch)
        logsBatch = ArrayList()
        loggingEndPoint?.let { HyperNetworking.makePostRequest(it, logsToSend) }
    }
}
