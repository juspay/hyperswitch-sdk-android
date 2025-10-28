package io.hyperswitch.webview.utils

class WritableNativeMapKeySetIterator(
    private val iterator: Iterator<String>,
) : ReadableMapKeySetIterator {
    override fun hasNextKey(): Boolean = iterator.hasNext()

    override fun nextKey(): String = iterator.next()
}
