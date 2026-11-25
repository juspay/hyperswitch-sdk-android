package io.hyperswitch.paymentsheet

import android.os.Bundle

internal object PaymentMessageWire {
    fun style(style: PaymentSheet.MessageElementStyle): Bundle = Bundle().apply {
        toHexColorString(style.color)?.let { putString("color", it) }
        style.fontSize?.takeIf { it.isFinite() && it >= 0f }?.let { putDouble("fontSize", it.toDouble()) }
        // Typeface's 1..1000 range, rounded to the nearest weight the bundle supports, as iOS does.
        style.fontWeight?.takeIf { it in 1..1000 }?.let {
            putString("fontWeight", ((it + 50) / 100 * 100).coerceIn(100, 900).toString())
        }
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
