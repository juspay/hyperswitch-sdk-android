package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.ConcurrentLinkedQueue

object HyperEventEmitter {
    private var reactContext: ReactApplicationContext? = null
    private val pendingEvents = ConcurrentLinkedQueue<Pair<String, Map<String, String?>>>()

    fun initialize(context: ReactApplicationContext) {
        reactContext = context
        processPendingEvents()
    }

    fun deinitialize() {
        reactContext = null
    }

    fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
        val writableMap = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                "true" -> {
                    writableMap.putBoolean(key, true)
                }
                "false" -> {
                    writableMap.putBoolean(key, false)
                }
                else -> {
                    writableMap.putString(key, value)
                }
            }
        }

        if (reactContext != null && reactContext!!.hasCatalystInstance()) {
            try {
                reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(tag, writableMap)
            } catch (e: Exception) {
                pendingEvents.add(Pair(tag, map))
            }
        } else {
            pendingEvents.add(Pair(tag, map))
        }
    }

    fun confirmCardStatic(map: MutableMap<String, String?>) {
        confirmStatic("confirm", map)
    }

    fun confirmECStatic(map: MutableMap<String, String?>) {
        confirmStatic("confirmEC", map)
    }

    private fun processPendingEvents() {
        if (reactContext?.hasCatalystInstance() != true || reactContext?.catalystInstance?.isDestroyed == true) {
            return
        }
        
        val iterator = pendingEvents.iterator()
        while (iterator.hasNext()) {
            val (tag, map) = iterator.next()
            val writableMap = Arguments.createMap()
            for ((key, value) in map) {
                 when (value) {
                    "true" -> {
                        writableMap.putBoolean(key, true)
                    }
                    "false" -> {
                        writableMap.putBoolean(key, false)
                    }
                    else -> {
                        writableMap.putString(key, value)
                    }
                }
            }
                reactContext!!.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(tag, writableMap)
                iterator.remove()
        }
    }
} 