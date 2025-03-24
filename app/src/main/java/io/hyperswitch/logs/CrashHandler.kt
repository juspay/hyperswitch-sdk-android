package io.hyperswitch.logs

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val fileManager = LogFileManager(context)
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val obj =
        mapOf(
            "label" to "crash_detected",
            "value" to throwable.toString(),
            "category" to "",
            "subcategory" to ""
        )
        try {
            val jsonData = JSONObject(obj).toString()
            val log = HSLog.LogBuilder().logType("error").category(LogCategory.USER_EVENT)
                .eventName(EventName.CRASH_EVENT).value(jsonData)
            HyperLogManager.addLog(log.build())
        } catch (_: JSONException) {
        }
        fileManager.addLog(HyperLogManager.getAllLogsAsString())
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
