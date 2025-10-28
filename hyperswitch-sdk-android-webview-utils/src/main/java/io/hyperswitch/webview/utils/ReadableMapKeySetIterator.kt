package io.hyperswitch.webview.utils

/** Interface of a iterator for a [NativeMap]'s key set. */
@DoNotStrip
public interface ReadableMapKeySetIterator {
    public fun hasNextKey(): Boolean

    public fun nextKey(): String
}
