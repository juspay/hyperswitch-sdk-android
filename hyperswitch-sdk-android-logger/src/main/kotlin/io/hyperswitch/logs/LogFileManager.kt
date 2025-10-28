package io.hyperswitch.logs

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException

class LogFileManager(private val context: Context) {

    private val crashLogFileName = "crash_logs.json"
    fun addLog(log: String) {
        try {
            val existingLogs = getAllLogs()
            val newLogsArray = JSONArray(log)
            for (i in 0 until newLogsArray.length()) {
                existingLogs.put(newLogsArray.get(i))
            }
            writeLogsToFile(existingLogs)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun getAllLogs(): JSONArray {
        return try {
            val file = File(context.filesDir, crashLogFileName)
            if (file.exists()) {
                val content = file.readText()
                JSONArray(content)
            } else {
                JSONArray()
            }
        } catch (e: JSONException) {
            JSONArray()
        } catch (e: IOException) {
            JSONArray()
        }
    }

    private fun writeLogsToFile(logs: JSONArray) {
        try {
            val file = File(context.filesDir, crashLogFileName)
            file.writeText(logs.toString()) // Write the logs as a JSON string
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    fun clearFile() {
        try {
            val file = File(context.filesDir, "crash_logs.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
