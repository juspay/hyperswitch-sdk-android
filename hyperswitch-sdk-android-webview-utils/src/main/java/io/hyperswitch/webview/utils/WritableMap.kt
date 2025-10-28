package io.hyperswitch.webview.utils

/** Interface for a mutable map. Used to pass arguments from Kotlin to JS. */
public interface WritableMap : ReadableMap {
    public fun copy(): WritableMap

    public fun merge(source: ReadableMap)

    public fun putArray(
        key: String,
        value: ReadableArray?,
    )

    public fun putBoolean(
        key: String,
        value: Boolean,
    )

    public fun putDouble(
        key: String,
        value: Double,
    )

    public fun putInt(
        key: String,
        value: Int,
    )

    public fun putLong(
        key: String,
        value: Long,
    )

    public fun putMap(
        key: String,
        value: ReadableMap?,
    )

    public fun putNull(key: String)

    public fun putString(
        key: String,
        value: String?,
    )
}
