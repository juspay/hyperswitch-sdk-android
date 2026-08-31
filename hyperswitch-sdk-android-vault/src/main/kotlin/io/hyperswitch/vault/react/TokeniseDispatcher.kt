package io.hyperswitch.vault.react

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Pending native `HyperswitchCollect.tokenise(completion)` calls awaiting
 * their JS answer (HyperVaultModule.submitTokeniseResult).
 *
 * One entry per requestId; resolved exactly once, on the main thread — either
 * by the JS bridge or by the timeout safety net (fires when no JS vault
 * surface answered, e.g. the runtime never started or no field is mounted).
 */
internal object TokeniseDispatcher {

    private const val TIMEOUT_MS = 30_000L

    /** Same shape the JS vault package returns for a not-mounted form. */
    private const val TIMEOUT_RESULT =
        """{"status":"not_ready","error":{"code":"not_ready","message":"The card form is not ready yet."}}"""

    private val nextId = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<Long, (String) -> Unit>()
    private val timeouts = ConcurrentHashMap<Long, Runnable>()

    fun newRequestId(): Long = nextId.getAndIncrement()

    fun register(requestId: Long, completion: (String) -> Unit) {
        pending[requestId] = completion
        val timeout = Runnable { resolve(requestId, TIMEOUT_RESULT) }
        timeouts[requestId] = timeout
        mainHandler.postDelayed(timeout, TIMEOUT_MS)
    }

    fun resolve(requestId: Long, resultJson: String) {
        val completion = pending.remove(requestId) ?: return
        timeouts.remove(requestId)?.let(mainHandler::removeCallbacks)
        mainHandler.post { completion(resultJson) }
    }
}
