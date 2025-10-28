package io.hyperswitch.webview.utils

import java.util.ArrayList

/**
 * Interface for an array that allows typed access to its members. Used to pass parameters from JS
 * to Kotlin.
 */
public interface ReadableArray {
    public fun getArray(index: Int): ReadableArray?

    public fun getBoolean(index: Int): Boolean

    public fun getDouble(index: Int): Double

    public fun getDynamic(index: Int): Dynamic

    public fun getInt(index: Int): Int

    public fun getLong(index: Int): Long

    public fun getMap(index: Int): ReadableMap?

    public fun getString(index: Int): String?

    public fun getType(index: Int): ReadableType

    public fun isNull(index: Int): Boolean

    public fun size(): Int

    public fun toArrayList(): ArrayList<Any?>
}
