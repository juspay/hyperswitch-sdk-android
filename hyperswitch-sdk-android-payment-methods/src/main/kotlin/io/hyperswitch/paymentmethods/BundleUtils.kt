package io.hyperswitch.paymentmethods

import android.os.Bundle

/** Minimal Map -> Bundle conversion used by the payment-methods module. */
internal object BundleUtils {

    fun toBundle(input: Map<String, Any?>): Bundle {
        val bundle = Bundle()
        for ((key, value) in input) {
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putFloat(key, value)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST")
                bundle.putBundle(key, toBundle(value as Map<String, Any?>))

                is List<*> -> bundle.putSerializable(key, toSerializableList(value))
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    private fun toSerializableList(list: List<*>): ArrayList<Any?> =
        ArrayList(list.map { item ->
            when (item) {
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") toBundle(item as Map<String, Any?>)
                is List<*> -> toSerializableList(item)
                else -> item
            }
        })
}
