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
        val eventData = mapOf(
            "label" to label,
            "value" to value.toString(),
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

    override fun track(
        category: String,
        subCategory: String,
        level: String,
        label: String,
        key: String,
        value: Any
    ) {
        if (category == "lifecycle") {
            val eventName = when (key) {
                "init" -> EventName.HYPER_OTA_INIT
                "end" -> EventName.HYPER_OTA_FINISH
                else -> EventName.HYPER_OTA_EVENT
            }

            createAndSendLog(eventName, level, label, category, subCategory, value)
        }        
//        Log.i("ota -1",category + " " + subCategory + " " + level + " " + label + " " + key + " " + value)
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
            val eventName = when (key) {
                "init" -> EventName.HYPER_OTA_INIT
                "end" -> EventName.HYPER_OTA_FINISH
                else -> EventName.HYPER_OTA_EVENT
            }
            try {
                val otaVersion = value.getString("package_version")
                HyperLogManager.setOtaVersion(otaVersion)
            }catch (_: JSONException){

            }
            createAndSendLog(eventName, level, label, category, subCategory, value)
        }
//        Log.i("ota -2",category + " " + subCategory + " " + level + " " + label + " " + key + " " + value.toString())

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
                "value" to e.message.toString(),
                "category" to category,
                "subcategory" to subCategory
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
