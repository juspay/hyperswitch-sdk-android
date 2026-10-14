package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Code carried by every failure the SDK reports when it could not start. */
const val SDK_INIT_FAILED = "SDK_INIT_FAILED"

/**
 * Whether one React host (payments, payment methods, payment method management) could
 * start. A host fails to start when its JavaScript is missing from the app or cannot be
 * loaded; the SDK then reports [SDK_INIT_FAILED] from every call that needs the host
 * instead of crashing the app or waiting forever.
 *
 * The failure is set once, before the bundle has been evaluated: from the check made
 * before the host starts ([HyperBundleLoader.missingFiles]), from the bundle loader,
 * or from the host's exception handler. Thread-safe.
 */
class HostHealth internal constructor(private val hostName: String) {

    private val failure = AtomicReference<Throwable?>(null)
    private val listeners = CopyOnWriteArrayList<(Throwable) -> Unit>()

    @Volatile
    private var started = false

    /** Why the host could not start, or null while it can (or has not tried yet). */
    val initFailure: Throwable?
        get() = failure.get()

    /** The host's bundle is running; exceptions from now on are not start failures. */
    internal fun markStarted() {
        started = true
    }

    /** Records why the host cannot start. Only the first reason is kept and reported. */
    internal fun fail(reason: String, cause: Throwable? = null): Throwable {
        val error = IllegalStateException("$MESSAGE_PREFIX ($hostName): $reason", cause)
        if (!failure.compareAndSet(null, error)) return failure.get() ?: error
        log(error.message.orEmpty(), cause)
        val mainHandler = Handler(Looper.getMainLooper())
        listeners.forEach { listener -> mainHandler.post { listener(error) } }
        return error
    }

    /**
     * Calls [listener] on the main thread with the failure: now if the host has already
     * failed, else when it does. Returns what removes the listener.
     */
    internal fun onFailure(listener: (Throwable) -> Unit): () -> Unit {
        failure.get()?.let { error ->
            Handler(Looper.getMainLooper()).post { listener(error) }
            return {}
        }
        listeners.add(listener)
        // Failed between the check and the registration: deliver it all the same.
        failure.get()?.let { error ->
            if (listeners.remove(listener)) Handler(Looper.getMainLooper()).post { listener(error) }
        }
        return { listeners.remove(listener) }
    }

    /**
     * The host delegate's exception handler. React Native's default rethrows, which ends
     * the app; an SDK must not. Before the bundle runs, an exception means the host could
     * not start; afterwards it is logged, and React Native tears the host down.
     */
    internal fun handleInstanceException(error: Exception) {
        if (!started) {
            fail("the JavaScript bundle could not be loaded", error)
        } else {
            log("React host error ($hostName): ${error.message}", error)
        }
    }

    /** An error to hand a caller: [SDK_INIT_FAILED] as its cause, the codebase's convention. */
    internal fun resultError(): Throwable {
        val message = failure.get()?.message ?: "$MESSAGE_PREFIX ($hostName)"
        return Throwable(message).apply { initCause(Throwable(SDK_INIT_FAILED)) }
    }

    internal companion object {
        /** Starts the message of every start failure, so it can be recognised downstream. */
        const val MESSAGE_PREFIX = "Hyperswitch SDK failed to initialise"
    }

    private fun log(message: String, cause: Throwable?) {
        try {
            HyperLogManager.addLog(
                HSLog.LogBuilder()
                    .value(cause?.let { "$message: ${it.message}" } ?: message)
                    .category(LogCategory.API)
                    .logType("error")
                    .build()
            )
        } catch (_: Exception) {}
    }
}
