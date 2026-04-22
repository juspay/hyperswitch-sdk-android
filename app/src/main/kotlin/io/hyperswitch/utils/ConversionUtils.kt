package io.hyperswitch.utils

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ConversionUtils {

    @JvmStatic
    @Throws(JSONException::class)
    fun convertJsonToMap(jsonObject: JSONObject): WritableMap {
        val map = WritableNativeMap()

        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = jsonObject.get(key)
            if (value == null || value === JSONObject.NULL) {
                map.putNull(key)
                continue
            }
            when (value) {
                is JSONObject -> map.putMap(key, convertJsonToMap(value))
                is JSONArray -> map.putArray(key, convertJsonToArray(value))
                is Boolean -> map.putBoolean(key, value)
                is Int -> map.putInt(key, value)
                is Long -> map.putDouble(key, value.toDouble())
                is Float -> map.putDouble(key, value.toDouble())
                is Double -> map.putDouble(key, value)
                is String -> map.putString(key, value)
                else -> map.putString(key, value.toString())
            }
        }
        return map
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun convertJsonToArray(jsonArray: JSONArray): WritableArray {
        val array = WritableNativeArray()

        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            if (value == null || value === JSONObject.NULL) {
                array.pushNull()
                continue
            }
            when (value) {
                is JSONObject -> array.pushMap(convertJsonToMap(value))
                is JSONArray -> array.pushArray(convertJsonToArray(value))
                is Boolean -> array.pushBoolean(value)
                is Int -> array.pushInt(value)
                is Long -> array.pushDouble(value.toDouble())
                is Float -> array.pushDouble(value.toDouble())
                is Double -> array.pushDouble(value)
                is String -> array.pushString(value)
                else -> array.pushString(value.toString())
            }
        }
        return array
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun convertMapToJson(readableMap: ReadableMap?): JSONObject {
        if (readableMap == null) return JSONObject()
        val obj = JSONObject()
        val iterator = readableMap.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (readableMap.getType(key)) {
                ReadableType.Null -> obj.put(key, JSONObject.NULL)
                ReadableType.Boolean -> obj.put(key, readableMap.getBoolean(key))
                ReadableType.Number -> obj.put(key, readableMap.getDouble(key))
                ReadableType.String -> obj.put(key, readableMap.getString(key))
                ReadableType.Map -> obj.put(key, convertMapToJson(readableMap.getMap(key)))
                ReadableType.Array -> obj.put(key, convertArrayToJson(readableMap.getArray(key)))
            }
        }
        return obj
    }

    /**
     * Recursively convert a [ReadableMap] into a plain [Map]<String, Any>.
     * Nested maps and arrays are converted recursively so that callers never
     * receive raw [org.json.JSONObject] / [org.json.JSONArray] values.
     */
    @JvmStatic
    fun readableMapToMap(readableMap: ReadableMap?): Map<String, Any> {
        if (readableMap == null) return emptyMap()
        val result = mutableMapOf<String, Any>()
        val iterator = readableMap.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (readableMap.getType(key)) {
                ReadableType.Null -> {}
                ReadableType.Boolean -> result[key] = readableMap.getBoolean(key)
                ReadableType.Number -> result[key] = readableMap.getDouble(key)
                ReadableType.String -> result[key] = readableMap.getString(key) ?: ""
                ReadableType.Map -> result[key] = readableMapToMap(readableMap.getMap(key))
                ReadableType.Array -> result[key] = readableArrayToList(readableMap.getArray(key))
            }
        }
        return result
    }

    @JvmStatic
    fun readableArrayToList(readableArray: ReadableArray?): List<Any> {
        if (readableArray == null) return emptyList()
        val result = mutableListOf<Any>()
        for (i in 0 until readableArray.size()) {
            when (readableArray.getType(i)) {
                ReadableType.Null -> {}
                ReadableType.Boolean -> result.add(readableArray.getBoolean(i))
                ReadableType.Number -> result.add(readableArray.getDouble(i))
                ReadableType.String -> result.add(readableArray.getString(i) ?: "")
                ReadableType.Map -> result.add(readableMapToMap(readableArray.getMap(i)))
                ReadableType.Array -> result.add(readableArrayToList(readableArray.getArray(i)))
            }
        }
        return result
    }

    /**
     * Recursively convert a plain [Map] into a [WritableMap] (ReadableMap).
     * Mirrors the inverse of [readableMapToMap].
     */
    @JvmStatic
    fun convertMapToReadableMap(map: Map<*, *>): WritableMap {
        val writableMap = WritableNativeMap()
        for ((key, value) in map) {
            val k = key?.toString() ?: continue
            when (value) {
                null -> writableMap.putNull(k)
                is Boolean -> writableMap.putBoolean(k, value)
                is Int -> writableMap.putInt(k, value)
                is Long -> writableMap.putDouble(k, value.toDouble())
                is Float -> writableMap.putDouble(k, value.toDouble())
                is Double -> writableMap.putDouble(k, value)
                is String -> writableMap.putString(k, value)
                is Map<*, *> -> writableMap.putMap(k, convertMapToReadableMap(value))
                is List<*> -> writableMap.putArray(k, convertListToReadableArray(value))
                else -> writableMap.putString(k, value.toString())
            }
        }
        return writableMap
    }

    /**
     * Recursively convert a plain [List] into a [WritableArray] (ReadableArray).
     */
    @JvmStatic
    fun convertListToReadableArray(list: List<*>): WritableArray {
        val writableArray = WritableNativeArray()
        for (value in list) {
            when (value) {
                null -> writableArray.pushNull()
                is Boolean -> writableArray.pushBoolean(value)
                is Int -> writableArray.pushInt(value)
                is Long -> writableArray.pushDouble(value.toDouble())
                is Float -> writableArray.pushDouble(value.toDouble())
                is Double -> writableArray.pushDouble(value)
                is String -> writableArray.pushString(value)
                is Map<*, *> -> writableArray.pushMap(convertMapToReadableMap(value))
                is List<*> -> writableArray.pushArray(convertListToReadableArray(value))
                else -> writableArray.pushString(value.toString())
            }
        }
        return writableArray
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun convertArrayToJson(readableArray: ReadableArray?): JSONArray {
        if (readableArray == null) return JSONArray()
        val array = JSONArray()
        for (i in 0 until readableArray.size()) {
            when (readableArray.getType(i)) {
                ReadableType.Null -> {}
                ReadableType.Boolean -> array.put(readableArray.getBoolean(i))
                ReadableType.Number -> array.put(readableArray.getDouble(i))
                ReadableType.String -> array.put(readableArray.getString(i))
                ReadableType.Map -> array.put(convertMapToJson(readableArray.getMap(i)))
                ReadableType.Array -> array.put(convertArrayToJson(readableArray.getArray(i)))
            }
        }
        return array
    }
}
