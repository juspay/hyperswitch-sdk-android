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
        message.text?.let {
            putParcelableArrayList("value", ArrayList(segments(message).map { (text, element) ->
                Bundle().apply {
                    putString("text", text)
                    if (element != null) {
                        putBoolean("tappable", true)
                        element.style?.let { putBundle("style", style(it)) }
                    }
                }
            }))
        }
        message.elementStyles?.let { defaults ->
            putBundle("elementStyles", Bundle().apply {
                defaults.action?.let { putBundle("action", style(it)) }
            })
        }
    }

    /* The message's `value`, as sent: text pieces alternate with the actions `include` marked,
       in order. A tap names a position in this list. */
    fun segments(message: PaymentSheet.PaymentMethodMessage): List<Pair<String, PaymentSheet.MessageElement?>> =
        buildList {
            message.text?.split(PaymentSheet.MessageBuilder.MARKER)?.forEachIndexed { index, piece ->
                if (piece.isNotEmpty()) add(piece to null)
                message.elements.getOrNull(index)?.let { add(it.label to it) }
            }
        }
}
