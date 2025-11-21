package io.hyperswitch.threeds.api

/**
 * Challenge completion event
 */
data class CompletionEvent(
    val transactionId: String
)

/**
 * Protocol error event during challenge
 */
data class ProtocolErrorEvent(
    val errorMessage: ErrorMessage
)

/**
 * Runtime error event during challenge
 */
data class RuntimeErrorEvent(
    val errorMessage: String
)

/**
 * Error message details
 */
data class ErrorMessage(
    val errorDescription: String
)

/**
 * Challenge status receiver interface
 */
interface ChallengeStatusReceiver {
    fun completed(completionEvent: CompletionEvent)
    fun cancelled()
    fun timedout()
    fun protocolError(protocolErrorEvent: ProtocolErrorEvent)
    fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent)
}
