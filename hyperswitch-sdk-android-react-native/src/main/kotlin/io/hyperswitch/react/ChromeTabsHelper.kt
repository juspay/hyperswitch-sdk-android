package io.hyperswitch.react

import android.content.Context
import android.content.Intent

/**
 * Reflection helper for react-native-inappbrowser-reborn classes.
 *
 * The library is provided at runtime by the host (:app) module through
 * React Native autolinking. Using reflection keeps :core from needing a
 * compile-time dependency on a specific version of the library.
 */
internal object ChromeTabsHelper {

    private const val DISMISSED_EVENT_CLASS = "com.proyecto26.inappbrowser.ChromeTabsDismissedEvent"
    private const val MANAGER_ACTIVITY_CLASS = "com.proyecto26.inappbrowser.ChromeTabsManagerActivity"

    fun createDismissedEvent(message: String?, resultType: String, isError: Boolean): Any? {
        return try {
            val clazz = Class.forName(DISMISSED_EVENT_CLASS)
            val constructor = clazz.getConstructor(
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            constructor.newInstance(message, resultType, isError)
        } catch (_: Exception) {
            null
        }
    }

    fun createDismissIntent(context: Context): Intent? {
        return try {
            val clazz = Class.forName(MANAGER_ACTIVITY_CLASS)
            val method = clazz.getMethod("createDismissIntent", Context::class.java)
            method.invoke(null, context) as? Intent
        } catch (_: Exception) {
            null
        }
    }
}
