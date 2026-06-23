package io.hyperswitch.model


sealed class ElementUpdateIntentResult {
    object Success : ElementUpdateIntentResult()
    object Cancelled : ElementUpdateIntentResult()
    data class Failure(val cause: Throwable) : ElementUpdateIntentResult()
}
