package io.hyperswitch.paymentsheet

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import java.util.Locale

internal object PaymentMessageWire {
    fun style(style: PaymentSheet.MessageElementStyle): Bundle = Bundle().apply {
        style.color?.let { color ->
            val rgb = String.format(Locale.ROOT, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
            putString("color", rgb + if (Color.alpha(color) < 255) String.format(Locale.ROOT, "%02X", Color.alpha(color)) else "")
        }
        style.fontSize?.let {
            if (it.isFinite() && it >= 0f) putDouble("fontSize", it.toDouble())
            else Log.w("Hyperswitch", "Message fontSize must be finite and nonnegative; ignoring it.")
        }
        style.fontWeight?.let { putString("fontWeight", it.value) }
        style.fontStyle?.let { putString("fontStyle", it.value) }
        style.textDecoration?.let { putString("textDecoration", it.value) }
    }

    fun message(message: PaymentSheet.PaymentMethodMessage): Bundle = Bundle().apply {
        putString("displayMode", message.displayMode.value)
        message.text?.let { putString("value", it) }
        message.elementStyles?.let { defaults ->
            putBundle("elementStyles", Bundle().apply {
                defaults.action?.let { putBundle("action", style(it)) }
            })
        }
        val actions = ArrayList(message.elements.map { element ->
            Bundle().apply {
                putString("key", element.key)
                putString("label", element.label)
                element.style?.let { putBundle("style", style(it)) }
            }
        })
        if (actions.isNotEmpty()) putBundle("elements", Bundle().apply {
            putParcelableArrayList("action", actions)
        })
    }
}
