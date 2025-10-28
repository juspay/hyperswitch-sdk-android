package io.hyperswitch.webview.utils

/** Defines the type of an object stored in a [ReadableArray] or [ReadableMap]. */
@DoNotStrip
public enum class ReadableType {
    Null,
    Boolean,
    Number,
    String,
    Map,
    Array,
}
