package io.hyperswitch.webview.utils

public fun interface Callback {
    public operator fun invoke(args: Map<String, Any?>)
}