package io.hyperswitch.webview.utils

/**
 * Type representing a piece of data with unknown runtime type. Useful for allowing javascript to
 * pass one of multiple types down to the native layer.
 */
public interface Dynamic {
    public val type: ReadableType

    public val isNull: Boolean

    public fun asArray(): ReadableArray?

    public fun asBoolean(): Boolean

    public fun asDouble(): Double

    public fun asInt(): Int

    public fun asMap(): ReadableMap?

    public fun asString(): String?

    public fun recycle()
}
