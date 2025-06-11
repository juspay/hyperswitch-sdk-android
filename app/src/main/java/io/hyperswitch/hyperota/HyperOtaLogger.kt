package io.hyperswitch.hyperota

import `in`.juspay.hyperota.TrackerCallback
import io.hyperswitch.BuildConfig
import io.hyperswitch.logs.EventName
import org.json.JSONObject
import io.hyperswitch.logs.HSLog
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
        key: String,
        value: Any
    ) {
        if (!value.toString().contains("index split is empty") && !value.toString()
                .contains("End of input at character") && !value.toString()
                .contains("some files during clean up")
        ) {
            val eventData = mapOf(
                "label" to label,
                "value" to value,
                "category" to category,
                "subcategory" to subCategory,
                "key" to key
            )

            try {
                val jsonData = JSONObject(eventData).toString()
                val log = HSLog.LogBuilder().logType(level).category(LogCategory.OTA_LIFE_CYCLE)
                    .eventName(eventName).value(jsonData).version(BuildConfig.VERSION_NAME)
                HyperLogManager.addLog(log.build())
            } catch (e: JSONException) {
            }
        }
    }

    override fun track(
        category: String, subCategory: String, level: String, label: String, key: String, value: Any
    ) {
        if (category == "lifecycle") {
            if (subCategory == "hyperota") {
                val eventName = when (key) {
                    "init" -> EventName.HYPER_OTA_INIT
                    "end" -> EventName.HYPER_OTA_FINISH
                    else -> EventName.HYPER_OTA_EVENT
                }
                createAndSendLog(eventName, level, label, category, subCategory, key, value)
            } else {
                createAndSendLog(
                    EventName.HYPER_OTA_EVENT, level, label, category, subCategory, key, value
                )
            }

        }
//        Log.i("ota-1" , "category : " + category + "\n subCategory : " + subCategory +
//                "\n level : " + level + "\n label : " + label + "\n key : " + key + "\n value : " + value.toString())
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
            } catch (_: JSONException) {

            }
            if (subCategory == "hyperota") {
                val eventName = when (key) {
                    "init" -> EventName.HYPER_OTA_INIT
                    "end" -> EventName.HYPER_OTA_FINISH
                    else -> EventName.HYPER_OTA_EVENT
                }
                createAndSendLog(eventName, level, label, category, subCategory, key, value)
            } else {
                createAndSendLog(
                    EventName.HYPER_OTA_EVENT, level, label, category, subCategory, key, value
                )
            }
        }
//        Log.i("ota-2" , "category : " + category + "\n subCategory : " + subCategory +
//                "\n level : " + level + "\n label : " + label + "\n key : " + key + "\n value : " + value.toString())
    }

    override fun trackException(
        category: String, subCategory: String, label: String, description: String, e: Throwable
    ) {
        if (category == "lifecycle") {
            val eventData = mapOf(
                "label" to label,
                "value" to (e.message ?: e.stackTraceToString()),
                "category" to category,
                "subcategory" to subCategory
            )

            try {
                val jsonData = JSONObject(eventData).toString()
                val log =
                    HSLog.LogBuilder().logType("error").category(LogCategory.OTA_LIFE_CYCLE)
                        .eventName(EventName.HYPER_OTA_EVENT).value(jsonData)

                HyperLogManager.addLog(log.build())
            } catch (e: JSONException) {
            }
        }
//        Log.i("ota-3" , "category : " + category + "\n subCategory : " + subCategory +
//                "\n level : " + "error" + "\n label : " + label + "\n description : " + description + "\n value : " + e.toString())
    }
}
