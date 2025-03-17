package io.hyperswitch.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hyperswitch.networking.HyperNetworking
import io.hyperswitch.react.Utils.Companion.getLoggingUrl
import kotlinx.coroutines.launch
import org.json.JSONArray

object HyperLogManager : ViewModel() {

    private val logsBatch = mutableListOf<HSLog>()
    private var publishableKey: String? = null
    private var loggingEndPoint: String? = null
    private const val DEFAULT_DELAY_IN_MILLIS = 2000L
    private var delayInMillis: Long = DEFAULT_DELAY_IN_MILLIS
    private var hyperOtaVersion: String = ""
    private val debouncer = Debouncer(DEFAULT_DELAY_IN_MILLIS)

    fun initialise(
        pkKey: String,
        customLogUrl: String?,
        delay: Long = DEFAULT_DELAY_IN_MILLIS
    ) {
        if (pkKey.isNotBlank() ) {
            publishableKey = pkKey
            delayInMillis = delay
            if (customLogUrl != "" && customLogUrl != null){
                loggingEndPoint = customLogUrl
            }else{
                loggingEndPoint = getLoggingUrl(pkKey)
            }
        }
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
                    viewModelScope.launch {
                        try {
                            HyperNetworking.makePostRequest(endpoint, logArray.toString()).onSuccess {
                                fileManager.clearFile()
                            }
                        }catch (e :Exception){
                        }
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
                put("client_core_version", hyperOtaVersion.substringAfter("-"))
            }
        }
    }

    fun getAllLogsAsString(): String = logsBatch.joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun sendLogsOverNetwork() {
        if (logsBatch.isNotEmpty() && !publishableKey.isNullOrBlank() && !loggingEndPoint.isNullOrBlank()) {
            logsBatch.map { log ->
                log.apply {
                    merchantId = publishableKey!!
                    codePushVersion = hyperOtaVersion
                    clientCoreVersion = hyperOtaVersion.substringBefore('-')
                }
            }

            val logsToSend = getAllLogsAsString()
            logsBatch.clear()
            loggingEndPoint?.let { endpoint ->
                viewModelScope.launch {
                    try {
                        HyperNetworking.makePostRequest(endpoint, logsToSend)
                    }catch (e :Exception){
                    }
                }
            }
        } else {
            debouncedPushLogs()
        }
    }

}
