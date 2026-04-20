package io.hyperswitch.events

import org.json.JSONObject

data class EventResult(
    val eventName: String,
    val payload: JSONObject? = null
)