package io.hyperswitch.airborne

import `in`.juspay.hyperota.TrackerCallback
import io.hyperswitch.logs.EventName
import org.json.JSONObject
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import org.json.JSONException

/**
 * Logger implementation for tracking Airborne OTA lifecycle events and errors.
 * Extends TrackerCallback to capture OTA-related events and send them to the logging endpoint.
 *
 * @param sdkVersion The version of the SDK being used
 */
class AirborneLogger(private val sdkVersion: String) : TrackerCallback() {

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
                val log = HSLog.LogBuilder().logType(level).category(LogCategory.AIRBORNE_LIFE_CYCLE)
                    .eventName(eventName).value(jsonData).version(this.sdkVersion)
                HyperLogManager.addLog(log.build())
            } catch (_: JSONException) {
            }
        }
    }

    /**
     * Resolves the appropriate EventName based on the event key.
     *
     * Maps lifecycle event keys from the Airborne SDK to internal event names:
     * - "started" / "init" → AIRBORNE_INIT
     * - "end" → AIRBORNE_FINISH
     * - "boot" → AIRBORNE_BOOT
     * - everything else → AIRBORNE_EVENT
     */
    private fun resolveEventName(key: String): EventName {
        return when (key) {
            "started", "init" -> EventName.AIRBORNE_INIT
            "end" -> EventName.AIRBORNE_FINISH
            "boot" -> EventName.AIRBORNE_BOOT
            else -> EventName.AIRBORNE_EVENT
        }
    }

    /**
     * Tracks Airborne lifecycle events with primitive or string values.
     *
     * @param category The event category (e.g., "lifecycle")
     * @param subCategory The event subcategory (e.g., "hyperota", "hypersdk")
     * @param level The log level (e.g., "info", "debug", "error")
     * @param label A descriptive label for the event
     * @param key The event key (e.g., "started", "end", "boot")
     * @param value The event value to be logged
     */
    override fun track(
        category: String, subCategory: String, level: String, label: String, key: String, value: Any
    ) {
        if (category == "lifecycle") {
            if (subCategory == "hyperota" || subCategory == "hypersdk") {
                val eventName = resolveEventName(key)
                createAndSendLog(eventName, level, label, category, subCategory, key, value)
            } else {
                createAndSendLog(
                    EventName.AIRBORNE_EVENT, level, label, category, subCategory, key, value
                )
            }
        }
    }

    /**
     * Tracks Airborne lifecycle events with JSON object values.
     * Extracts OTA version from the value if available and updates the log manager.
     *
     * @param category The event category (e.g., "lifecycle")
     * @param subCategory The event subcategory (e.g., "hyperota", "hypersdk")
     * @param level The log level (e.g., "info", "debug", "error")
     * @param label A descriptive label for the event
     * @param key The event key (e.g., "started", "end", "boot")
     * @param value The JSON object containing event data
     */
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
            if (subCategory == "hyperota" || subCategory == "hypersdk") {
                val eventName = resolveEventName(key)
                createAndSendLog(eventName, level, label, category, subCategory, key, value)
            } else {
                createAndSendLog(
                    EventName.AIRBORNE_EVENT, level, label, category, subCategory, key, value
                )
            }
        }
    }

    /**
     * Tracks exceptions that occur during Airborne lifecycle operations.
     *
     * @param category The event category (e.g., "lifecycle")
     * @param subCategory The event subcategory
     * @param label A descriptive label for the exception
     * @param description A description of the exception context
     * @param e The throwable exception to be logged
     */
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
                    HSLog.LogBuilder().logType("error").category(LogCategory.AIRBORNE_LIFE_CYCLE)
                        .eventName(EventName.AIRBORNE_EVENT).value(jsonData)

                HyperLogManager.addLog(log.build())
            } catch (_: JSONException) {
            }
        }
    }
}
