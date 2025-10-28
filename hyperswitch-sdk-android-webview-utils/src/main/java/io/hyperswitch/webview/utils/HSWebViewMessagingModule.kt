package io.hyperswitch.webview.utils

internal interface HSWebViewMessagingModule {
    fun onShouldStartLoadWithRequest(event: WritableMap)

    fun onMessage(event: WritableMap)
}
