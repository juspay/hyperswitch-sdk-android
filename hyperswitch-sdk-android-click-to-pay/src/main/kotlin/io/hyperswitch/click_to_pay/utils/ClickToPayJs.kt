package io.hyperswitch.click_to_pay.utils

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Builds JavaScript literals for values passed into the Click to Pay WebViews.
 *
 * Values must never be concatenated into JavaScript source as raw text: a quote,
 * backslash or `</script>` inside a server-, merchant- or user-supplied value would
 * end its string literal and run as code in a WebView that handles card data.
 * Every literal produced here is JSON (which is valid JavaScript) and is also safe
 * inside an inline HTML `<script>` block.
 */
internal object ClickToPayJs {

    /** A JavaScript string literal for [value]. */
    fun string(value: String): String = harden(JSONObject.quote(value))

    /** A JavaScript object literal containing the [entries] whose value is not null. */
    fun obj(vararg entries: Pair<String, Any?>): String {
        val json = JSONObject()
        entries.forEach { (key, value) -> if (value != null) json.put(key, value) }
        return harden(json.toString())
    }

    /**
     * A JavaScript literal for a value received as JSON text, such as the token
     * returned by the UCTP session. Objects and arrays are re-serialised, so only
     * well-formed data reaches the page; anything else is passed as a string.
     */
    fun jsonValue(raw: String): String {
        val parsed = try {
            JSONTokener(raw).nextValue()
        } catch (_: Exception) {
            null
        }
        return when (parsed) {
            is JSONObject, is JSONArray -> harden(parsed.toString())
            else -> string(raw)
        }
    }

    // JSON escapes quotes, backslashes and control characters. Inside an HTML
    // <script> block, "</script>" and "<!--" still end or change the script, and
    // U+2028/U+2029 end lines in older JavaScript engines, so escape those too.
    private fun harden(json: String): String = buildString(json.length) {
        for (c in json) {
            when (c) {
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(c)
            }
        }
    }
}
