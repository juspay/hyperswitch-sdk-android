package io.hyperswitch.webview.utils

import java.util.ArrayList
import java.util.HashMap
import kotlin.collections.iterator

@DoNotStrip
public object Arguments {
    @JvmStatic public fun createMap(): WritableMap = WritableNativeMap()

    @JvmStatic public fun createArray(): WritableArray = WritableNativeArray()
}

class WritableNativeMap : WritableMap {
    private val map: HashMap<String, Any?> = HashMap()

    override fun copy(): WritableMap {
        val copy = WritableNativeMap()
        for ((key, value) in map) {
            when (value) {
                is WritableNativeMap -> copy.putMap(key, value.copy())
                is ReadableMap -> copy.putMap(key, (value as? WritableMap)?.copy())
                is WritableNativeArray -> copy.putArray(key, value.copy())
                is ReadableArray -> copy.putArray(key, value)
                else -> copy.map[key] = value
            }
        }
        return copy
    }

    override fun merge(source: ReadableMap) {
        val iterator = source.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            val type = source.getType(key)
            when (type) {
                ReadableType.Null -> putNull(key)
                ReadableType.Boolean -> putBoolean(key, source.getBoolean(key))
                ReadableType.Number -> putDouble(key, source.getDouble(key))
                ReadableType.String -> putString(key, source.getString(key))
                ReadableType.Map -> putMap(key, source.getMap(key))
                ReadableType.Array -> putArray(key, source.getArray(key))
            }
        }
    }

    override fun putArray(
        key: String,
        value: ReadableArray?,
    ) {
        map[key] = value
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        map[key] = value
    }

    override fun putDouble(
        key: String,
        value: Double,
    ) {
        map[key] = value
    }

    override fun putInt(
        key: String,
        value: Int,
    ) {
        map[key] = value
    }

    override fun putLong(
        key: String,
        value: Long,
    ) {
        map[key] = value
    }

    override fun putMap(
        key: String,
        value: ReadableMap?,
    ) {
        map[key] = value
    }

    override fun putNull(key: String) {
        map[key] = null
    }

    override fun putString(
        key: String,
        value: String?,
    ) {
        map[key] = value
    }

    override val entryIterator: Iterator<Map.Entry<String, Any?>>
        get() = map.entries.iterator()

    override fun getArray(name: String): ReadableArray? = map[name] as? ReadableArray

    override fun getBoolean(name: String): Boolean = map[name] as? Boolean ?: false

    override fun getDouble(name: String): Double {
        val value = map[name]
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            else -> 0.0
        }
    }

    override fun getDynamic(name: String): Dynamic = DynamicImpl(map[name])

    override fun getInt(name: String): Int {
        val value = map[name]
        return when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Long -> value.toInt()
            is Float -> value.toInt()
            else -> 0
        }
    }

    override fun getLong(name: String): Long {
        val value = map[name]
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            else -> 0L
        }
    }

    override fun getMap(name: String): ReadableMap? = map[name] as? ReadableMap

    override fun getString(name: String): String? = map[name] as? String

    override fun getType(name: String): ReadableType {
        val value = map[name]
        return when (value) {
            null -> ReadableType.Null
            is Boolean -> ReadableType.Boolean
            is Number -> ReadableType.Number
            is String -> ReadableType.String
            is ReadableMap -> ReadableType.Map
            is ReadableArray -> ReadableType.Array
            else -> ReadableType.Null
        }
    }

    override fun hasKey(name: String): Boolean = map.containsKey(name)

    override fun isNull(name: String): Boolean = map.containsKey(name) && map[name] == null

    override fun keySetIterator(): ReadableMapKeySetIterator = WritableNativeMapKeySetIterator(map.keys.iterator())

    override fun toHashMap(): HashMap<String, Any?> {
        val hashMap = HashMap<String, Any?>()
        for ((key, value) in map) {
            when (value) {
                is ReadableMap -> hashMap[key] = value.toHashMap()
                is ReadableArray -> hashMap[key] = value.toArrayList()
                else -> hashMap[key] = value
            }
        }
        return hashMap
    }
}

class WritableNativeArray : WritableArray {
    private val array: ArrayList<Any?> = ArrayList()

    fun copy(): WritableNativeArray {
        val copy = WritableNativeArray()
        for (value in array) {
            when (value) {
                is WritableNativeMap -> copy.pushMap(value.copy())
                is ReadableMap -> copy.pushMap(value)
                is WritableNativeArray -> copy.pushArray(value.copy())
                is ReadableArray -> copy.pushArray(value)
                is Boolean -> copy.pushBoolean(value)
                is Number -> copy.pushDouble(value.toDouble())
                is String -> copy.pushString(value)
                null -> copy.pushNull()
                else -> copy.array.add(value)
            }
        }
        return copy
    }

    override fun pushArray(array: ReadableArray?) {
        this.array.add(array)
    }

    override fun pushBoolean(value: Boolean) {
        array.add(value)
    }

    override fun pushDouble(value: Double) {
        array.add(value)
    }

    override fun pushInt(value: Int) {
        array.add(value)
    }

    override fun pushLong(value: Long) {
        array.add(value)
    }

    override fun pushMap(map: ReadableMap?) {
        array.add(map)
    }

    override fun pushNull() {
        array.add(null)
    }

    override fun pushString(value: String?) {
        array.add(value)
    }

    override fun getArray(index: Int): ReadableArray? = array.getOrNull(index) as? ReadableArray

    override fun getBoolean(index: Int): Boolean = array.getOrNull(index) as? Boolean ?: false

    override fun getDouble(index: Int): Double {
        val value = array.getOrNull(index)
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            else -> 0.0
        }
    }

    override fun getDynamic(index: Int): Dynamic = DynamicImpl(array.getOrNull(index))

    override fun getInt(index: Int): Int {
        val value = array.getOrNull(index)
        return when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Long -> value.toInt()
            is Float -> value.toInt()
            else -> 0
        }
    }

    override fun getLong(index: Int): Long {
        val value = array.getOrNull(index)
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            else -> 0L
        }
    }

    override fun getMap(index: Int): ReadableMap? = array.getOrNull(index) as? ReadableMap

    override fun getString(index: Int): String? = array.getOrNull(index) as? String

    override fun getType(index: Int): ReadableType {
        val value = array.getOrNull(index)
        return when (value) {
            null -> ReadableType.Null
            is Boolean -> ReadableType.Boolean
            is Number -> ReadableType.Number
            is String -> ReadableType.String
            is ReadableMap -> ReadableType.Map
            is ReadableArray -> ReadableType.Array
            else -> ReadableType.Null
        }
    }

    override fun isNull(index: Int): Boolean = index < array.size && array[index] == null

    override fun size(): Int = array.size

    override fun toArrayList(): ArrayList<Any?> {
        val arrayList = ArrayList<Any?>()
        for (value in array) {
            when (value) {
                is ReadableMap -> arrayList.add(value.toHashMap())
                is ReadableArray -> arrayList.add(value.toArrayList())
                else -> arrayList.add(value)
            }
        }
        return arrayList
    }
}
