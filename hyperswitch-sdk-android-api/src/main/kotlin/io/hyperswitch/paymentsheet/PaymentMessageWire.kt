package io.hyperswitch.paymentsheet

import android.graphics.Color
import android.os.Bundle
import java.util.Locale

internal object PaymentMessageWire {
    fun style(style: PaymentSheet.MessageElementStyle): Bundle = Bundle().apply {
        style.color?.let { color ->
            val rgb = String.format(Locale.ROOT, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
            putString("color", rgb + if (Color.alpha(color) < 255) String.format(Locale.ROOT, "%02X", Color.alpha(color)) else "")
        }
        style.fontSize?.takeIf { it.isFinite() && it >= 0f }?.let { putDouble("fontSize", it.toDouble()) }
        style.fontWeight?.let { putString("fontWeight", it.value) }
        style.fontStyle?.let { putString("fontStyle", it.value) }
        style.textDecoration?.let { putString("textDecoration", it.value) }
    }

    fun message(message: PaymentSheet.PaymentMethodMessage): Bundle = Bundle().apply {
        message.displayMode?.let { putString("displayMode", it.value) }
        message.text?.let { putParcelableArrayList("value", segments(it, message.elements)) }
        message.elementStyles?.let { defaults ->
            putBundle("elementStyles", Bundle().apply {
                defaults.action?.let { putBundle("action", style(it)) }
            })
        }
    }

    /* Text pieces alternate with the actions that `include` marked, in order. */
    private fun segments(text: String, elements: List<PaymentSheet.MessageElement>) = ArrayList<Bundle>().apply {
        text.split(PaymentSheet.MessageBuilder.MARKER).forEachIndexed { index, piece ->
            if (piece.isNotEmpty()) add(Bundle().apply { putString("text", piece) })
            elements.getOrNull(index)?.let { element ->
                add(Bundle().apply {
                    putString("text", element.label)
                    putBoolean("tappable", true)
                    element.style?.let { putBundle("style", style(it)) }
                })
            }
        }
    }
}
