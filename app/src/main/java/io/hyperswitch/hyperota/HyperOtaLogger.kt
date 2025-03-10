package io.hyperswitch.hyperota

import android.util.Log
import `in`.juspay.hyperota.TrackerCallback
import io.hyperswitch.logs.EventName
import org.json.JSONObject
import io.hyperswitch.logs.Log as nativeLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import org.json.JSONException

class HyperOtaLogger : TrackerCallback() {

    private fun createAndSendLog(
        eventName: EventName,
        level: String,
        label: String,
        category: String,
        subCategory: String,
        value: Any
    ) {
        if (!value.toString().contains("index split is empty") && !value.toString().contains("End of input at character") && !value.toString().contains("some files during clean up")) {
            val eventData = mapOf(
                "label" to label,
                "value" to value,
                "category" to category,
                "subcategory" to subCategory
            )

            try {
                val jsonData = JSONObject(eventData).toString()
                val log = nativeLog.LogBuilder()
                    .logType(level)
                    .category(LogCategory.OTA_LIFE_CYCLE)
                    .eventName(eventName)
                    .value(jsonData)
                HyperLogManager.addLog(log.build())
            } catch (e: JSONException) {
                Log.e("HyperOtaLogger", "Error: JSON serialization failed. ${e.message}")
            }
        }
    }

    override fun track(
        category: String,
        subCategory: String,
        level: String,
        label: String,
        key: String,
        value: Any
    ) {
        if (category == "lifecycle") {
            if (subCategory == "system") {
                val eventName = when (key) {
                    "init" -> EventName.HYPER_OTA_INIT
                    "end" -> EventName.HYPER_OTA_FINISH
                    else -> EventName.HYPER_OTA_EVENT
                }
                createAndSendLog(eventName, level, label, category, "update_task", value)
            }else{
                createAndSendLog(EventName.HYPER_OTA_EVENT, level, label, category, "update_task", value)
            }

        }
    }

    override fun track(
        category: String,
        subCategory: String,
        level: String,
        label: String,
        key: String,
        value: JSONObject
    ) {
        if (category == "lifecycle") {
            try {
                val otaVersion = value.getString("package_version")
                HyperLogManager.setOtaVersion(otaVersion)
            }catch (_: JSONException){

            }
            if (subCategory == "system") {
                val eventName = when (key) {
                    "init" -> EventName.HYPER_OTA_INIT
                    "end" -> EventName.HYPER_OTA_FINISH
                    else -> EventName.HYPER_OTA_EVENT
                }
                createAndSendLog(eventName, level, label, category, "update_task", value)
            }else{
                createAndSendLog(EventName.HYPER_OTA_EVENT, level, label, category, "update_task", value)
            }
        }
    }

    override fun trackException(
        category: String,
        subCategory: String,
        label: String,
        description: String,
        e: Throwable
    ) {
        if (category == "lifecycle") {
            val eventData = mapOf(
                "label" to label,
                "value" to (e.localizedMessage?.toString() ?: e.stackTraceToString()),
                "category" to category,
                "subcategory" to "update_task"
            )

            try {
                val jsonData = JSONObject(eventData).toString()
                val log = nativeLog.LogBuilder()
                    .logType("error")
                    .category(LogCategory.OTA_LIFE_CYCLE)
                    .eventName(EventName.HYPER_OTA_EVENT)
                    .value(jsonData)

                HyperLogManager.addLog(log.build())
            } catch (e: JSONException) {
                Log.e("HyperOtaLogger", "Error: JSON serialization failed. ${e.message}")
            }
        }
//        Log.i("ota -3",category + " " + subCategory  + " " + label + " " + e.toString())
    }
}
