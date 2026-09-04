package io.hyperswitch.paymentsession

import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class PaymentSessionRouter {

    private val sessionCallbackRef = AtomicReference<((PaymentSessionHandler) -> Unit)?>(null)
    private val sdkAuthorizationRef = AtomicReference<String?>(null)
    private val exitCallbacks = ConcurrentHashMap<Int, (PaymentResult) -> Unit>()

    fun setSessionCallback(sdkAuthorization: String?, newCallback: ((PaymentSessionHandler) -> Unit)?) {
        sessionCallbackRef.set(newCallback)
        sdkAuthorizationRef.set(sdkAuthorization)
    }

    fun getSdkAuthorization(): String = sdkAuthorizationRef.get() ?: ""

    fun executeSessionCallback(data: PaymentSessionHandler) {
        sessionCallbackRef.getAndSet(null)?.invoke(data)
            ?: println("No callback set")
    }

    fun tryRegisterExitCallback(rootTag: Int, callback: (PaymentResult) -> Unit): Boolean {
        return if (rootTag == -1) {
            exitCallbacks[-1] = callback
            true
        } else {
            exitCallbacks.putIfAbsent(rootTag, callback) == null
        }
    }

    fun executeExitCallback(rootTag: Int, data: String): Boolean {
        val cb = exitCallbacks.remove(rootTag) ?: exitCallbacks.remove(-1)

        val result = parseResult(data)
        cb?.invoke(result)
        return true
    }

    fun clearExitCallback(rootTag: Int) {
        exitCallbacks.remove(rootTag)
    }

    private fun parseResult(data: String): PaymentResult {
        val message = JSONObject(data)
        return when (val status = message.getString("status")) {
            "cancelled" -> PaymentResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val throwable = Throwable(message.getString("message"))
                throwable.initCause(Throwable(message.getString("code")))
                PaymentResult.Failed(throwable)
            }

            else -> PaymentResult.Completed(status ?: "default")
        }
    }

    /* Prefetch completion — the one addition over main. A single in-flight prefetch:
       fetchPrefetch registers a deferred before launching the JS headless task and
       completePrefetch(rootTag, data) resolves it; the payload itself lives only in the
       JS PrefetchCache. */
    private val prefetchCallbackRef = AtomicReference<CompletableDeferred<ReadableMap>?>()

    fun tryRegisterPrefetchCallback(deferred: CompletableDeferred<ReadableMap>): Boolean =
        prefetchCallbackRef.compareAndSet(null, deferred)

    fun completePrefetchCallback(data: ReadableMap): Boolean {
        val deferred = prefetchCallbackRef.getAndSet(null) ?: return false
        return deferred.complete(data)
    }

    fun failPrefetchCallback(error: Throwable): Boolean {
        val deferred = prefetchCallbackRef.getAndSet(null) ?: return false
        return deferred.completeExceptionally(error)
    }

    fun clearPrefetchCallback(deferred: CompletableDeferred<ReadableMap>) {
        prefetchCallbackRef.compareAndSet(deferred, null)
    }
}
