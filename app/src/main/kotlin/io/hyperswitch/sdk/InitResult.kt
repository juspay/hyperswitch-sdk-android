package io.hyperswitch.sdk

/* Public result wrapper for the Java-friendly callback flavors: kotlin.Result is
   opaque to Java callers, so session init failures (e.g. SESSION_INIT_IN_PROGRESS)
   surface through this type instead. */
sealed class InitResult<out T> {
    class Success<out T>(val value: T) : InitResult<T>()
    class Failure(val error: Throwable) : InitResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.value

    fun exceptionOrNull(): Throwable? = (this as? Failure)?.error
}
