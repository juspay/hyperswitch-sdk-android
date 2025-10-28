package io.hyperswitch.webview.utils

class DynamicImpl(
    private val value: Any?,
) : Dynamic {
    override val type: ReadableType
        get() =
            when (value) {
                null -> ReadableType.Null
                is Boolean -> ReadableType.Boolean
                is Number -> ReadableType.Number
                is String -> ReadableType.String
                is ReadableMap -> ReadableType.Map
                is ReadableArray -> ReadableType.Array
                else -> ReadableType.Null
            }

    override val isNull: Boolean
        get() = value == null

    override fun asArray(): ReadableArray? = value as? ReadableArray

    override fun asBoolean(): Boolean = value as? Boolean ?: false

    override fun asDouble(): Double =
        when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            else -> 0.0
        }

    override fun asInt(): Int =
        when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Long -> value.toInt()
            is Float -> value.toInt()
            else -> 0
        }

    override fun asMap(): ReadableMap? = value as? ReadableMap

    override fun asString(): String? = value as? String

    override fun recycle() {
        // No-op for now, can be used for object pooling if needed
    }
}
