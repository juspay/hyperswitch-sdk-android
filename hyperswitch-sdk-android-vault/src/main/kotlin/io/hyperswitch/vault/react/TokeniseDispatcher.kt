package io.hyperswitch.vault.react

import android.os.Handler
import android.os.Looper

/**
 * Pending native `HyperswitchCollect.tokenise(completion)` awaiting the JS
 * answer (HyperVaultModule.returnTokenizedValue).
 *
 * One in-flight tokenise per vault SDK instance; a fresh registration
 * replaces any earlier completion. The timeout safety net guarantees the
 * completion fires exactly once even when no JS vault surface is mounted to
 * answer.
 */
internal object TokeniseDispatcher {

    private const val TIMEOUT_MS = 30_000L

    /** Same shape the JS vault package returns for a not-mounted form. */
    private const val TIMEOUT_RESULT =
        """{"status":"not_ready","error":{"code":"not_ready","message":"The card form is not ready yet."}}"""

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: ((String) -> Unit)? = null
    private var timeout: Runnable? = null

    @Synchronized
    fun register(completion: (String) -> Unit) {
        timeout?.let(mainHandler::removeCallbacks)
        val t = Runnable { resolve(TIMEOUT_RESULT) }
        pending = completion
        timeout = t
        mainHandler.postDelayed(t, TIMEOUT_MS)
    }

    @Synchronized
    fun resolve(resultJson: String) {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        val completion = pending ?: return
        pending = null
        mainHandler.post { completion(resultJson) }
    }
}
