package io.hyperswitch.logs

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException

class LogFileManager(private val context: Context) {

    private val crashLogFileName = "crash_logs.json"

    // Add new logs to the file as part of a single JSON array
    fun addLog(log: String) {
        try {
            // Get existing logs
            val existingLogs = getAllLogs()

            // Parse the new log
            val newLogsArray = JSONArray(log)

            // Add all new logs to the existing logs array
            for (i in 0 until newLogsArray.length()) {
                existingLogs.put(newLogsArray.get(i))
            }

            // Write the updated logs back to the file
            writeLogsToFile(existingLogs)

        } catch (e: JSONException) {
            println("Error parsing new logs: ${e.message}")
            e.printStackTrace()
        }
    }

    // Retrieve all logs as a single JSON array
    fun getAllLogs(): JSONArray {
        return try {
            val file = File(context.filesDir, crashLogFileName)
            if (file.exists()) {
                val content = file.readText()
                println("Existing logs loaded from file: $content")
                JSONArray(content) // Parse the content as JSON array
            } else {
                println("Log file does not exist. Returning empty JSONArray.")
                JSONArray() // Return an empty array if file doesn't exist
            }
        } catch (e: JSONException) {
            println("Error parsing existing logs: ${e.message}")
            JSONArray() // Return an empty array on JSON parse failure
        } catch (e: IOException) {
            println("Error reading the log file: ${e.message}")
            JSONArray() // Return an empty array on file read failure
        }
    }

    // Write the unified logs back to the file
    private fun writeLogsToFile(logs: JSONArray) {
        try {
            val file = File(context.filesDir, crashLogFileName)
            file.writeText(logs.toString()) // Write the logs as a JSON string
            println("Logs successfully written to file: ${file.absolutePath}")
            println("Current file content: ${logs.toString()}")
        } catch (e: IOException) {
            println("Error writing logs to file: ${e.message}")
            e.printStackTrace()
        }
    }
    fun clearFile() {
        try {
            val file = File(context.filesDir, "crash_logs.json")
            if (file.exists()) {
                file.delete()
                println("Crash log file cleared successfully.")
            } else {
                println("Crash log file does not exist.")
            }
        } catch (e: IOException) {
            println("Error clearing crash log file: ${e.message}")
            e.printStackTrace()
        }
    }
}
