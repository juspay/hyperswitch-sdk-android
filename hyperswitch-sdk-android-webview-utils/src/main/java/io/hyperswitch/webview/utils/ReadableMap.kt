package io.hyperswitch.webview.utils

import java.util.HashMap
import kotlin.collections.Iterator
import kotlin.collections.Map

/**
 * Interface for a map that allows typed access to its members. Used to pass parameters from JS to
 * Kotlin.
 */
public interface ReadableMap {
    public val entryIterator: Iterator<Map.Entry<String, Any?>>

    public fun getArray(name: String): ReadableArray?

    public fun getBoolean(name: String): Boolean

    public fun getDouble(name: String): Double

    public fun getDynamic(name: String): Dynamic

    public fun getInt(name: String): Int

    public fun getLong(name: String): Long

    public fun getMap(name: String): ReadableMap?

    public fun getString(name: String): String?

    public fun getType(name: String): ReadableType

    public fun hasKey(name: String): Boolean

    public fun isNull(name: String): Boolean

    public fun keySetIterator(): ReadableMapKeySetIterator

    public fun toHashMap(): HashMap<String, Any?>
}
