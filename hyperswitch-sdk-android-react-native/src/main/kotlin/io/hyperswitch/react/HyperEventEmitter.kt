package io.hyperswitch.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Native → JS events for the shared host. Every payload carries the root tag of
 * the surface it is for; JS handlers compare it with their own. No listeners
 * live here: a surface's event listener belongs to the object that owns it.
 */
class HyperEventEmitter {
    private val moduleRef = AtomicReference<WeakReference<HyperModule>?>(null)

    fun attach(module: HyperModule) {
        moduleRef.set(WeakReference(module))
    }

    fun detach() {
        moduleRef.set(null)
    }

    fun emitEvent(tag: String, payload: WritableMap): Boolean {
        val module = moduleRef.get()?.get() ?: return false
        return try {
            module.emitEvent(tag, payload)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun confirm(tag: String, map: MutableMap<String, String?>) {
        emitEvent(tag, toWritableMap(map))
    }

    fun confirmCard(map: MutableMap<String, String?>) {
        confirm("confirm", map)
    }

    fun confirmEC(map: MutableMap<String, String?>) {
        confirm("confirmEC", map)
    }

    private fun toWritableMap(map: Map<String, String?>): WritableMap {
        val writableMap = Arguments.createMap()
        for ((key, value) in map) {
            when (value) {
                "true" -> writableMap.putBoolean(key, true)
                "false" -> writableMap.putBoolean(key, false)
                else -> writableMap.putString(key, value)
            }
        }
        return writableMap
    }
}
