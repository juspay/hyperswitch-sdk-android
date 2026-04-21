package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Elements internal constructor(
    activity: Activity,
    config: HyperswitchBaseConfiguration?,
    sessionConfiguration: PaymentSessionConfiguration
) {

    // Fix 1: thread-safe list
    private val hsElements: CopyOnWriteArrayList<HyperswitchBoundElement> = CopyOnWriteArrayList()

    private val paymentSession = PaymentSession(
        activity,
        config = config,
        sessionConfig = sessionConfiguration
    )

    fun bind(
        element: HyperswitchElement,
        configuration: PaymentSheet.Configuration? = null,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
    ): HyperswitchBoundElement {
        val hsElement = HyperswitchBoundElement(paymentSession, element, configuration, subscribe)
        hsElements.add(hsElement)
        return hsElement
    }

    fun retry(
        scope: CoroutineScope,
        failedElements: Map<HyperswitchBoundElement, Throwable>,
        sessionTokenProvider: suspend () -> String,
        onResult: (ElementsUpdateResult) -> Unit
    ) {
        updateIntentForElements(scope, failedElements.keys.toList(), sessionTokenProvider, onResult)
    }

    fun updateIntent(
        scope: CoroutineScope,
        sessionTokenProvider: suspend () -> String,
        onResult: (ElementsUpdateResult) -> Unit
    ) {
        updateIntentForElements(scope, hsElements.toList(), sessionTokenProvider, onResult)
    }

    private fun updateIntentForElements(
        scope: CoroutineScope,
        targets: List<HyperswitchBoundElement>,
        sessionTokenProvider: suspend () -> String,
        onResult: (ElementsUpdateResult) -> Unit
    ) {
        if (targets.isEmpty()) {
            onResult(ElementsUpdateResult.Success)
            return
        }

        scope.launch {

            // Phase 1: fan-out inits concurrently, capture per-element init failures
            val initResults: List<Pair<HyperswitchBoundElement, Result<Unit>>> = targets
                .map { hsElement ->
                    async {
                        hsElement to runCatching<Unit> {
                            suspendCancellableCoroutine { continuation ->
                                hsElement.updateIntentInit {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                    }
                }
                .awaitAll()

            val initSucceeded = initResults.filter { (_, r) -> r.isSuccess }.map { (e, _) -> e }
            val initFailed = initResults
                .filter { (_, r) -> r.isFailure }
                .associate { (e, r) -> e to (r.exceptionOrNull() ?: IllegalStateException("Init failed")) }

            // Fix 2: early-exit if all inits failed — no point fetching a token
            if (initSucceeded.isEmpty()) {
                onResult(
                    ElementsUpdateResult.TotalFailure(
                        cause = IllegalStateException("All ${targets.size} elements failed at init"),
                    )
                )
                return@launch
            }

            // Phase 2: single token fetch — if this throws, propagate as TotalFailure
            val sdkAuthorization = runCatching { sessionTokenProvider() }
                .getOrElse { tokenError ->
                    onResult(
                        ElementsUpdateResult.TotalFailure(
                            cause = tokenError,
                        )
                    )
                    return@launch
                }

            // Phase 3: fan-out completes only for init-succeeded elements
            val completeResults: List<Pair<HyperswitchBoundElement, Result<Unit>>> = initSucceeded
                .map { hsElement ->
                    async {
                        hsElement to runCatching<Unit> {
                            hsElement.updateIntentComplete(sdkAuthorization)
                        }
                    }
                }
                .awaitAll()

            val succeeded = completeResults.filter { (_, r) -> r.isSuccess }.map { (e, _) -> e }

            // Fix 3: merge init failures + complete failures for full picture
            val failed: Map<HyperswitchBoundElement, Throwable> = buildMap {
                putAll(initFailed)
                completeResults
                    .filter { (_, r) -> r.isFailure }
                    .forEach { (e, r) -> put(e, r.exceptionOrNull() ?: IllegalStateException("Complete failed")) }
            }

            val aggregated = when {
                failed.isEmpty() -> ElementsUpdateResult.Success
                succeeded.isEmpty() -> ElementsUpdateResult.TotalFailure(
                    cause = IllegalStateException("All ${targets.size} elements failed to update"),
                )
                else -> ElementsUpdateResult.PartialFailure(
                    succeeded = succeeded,
                    failed = failed
                )
            }

            onResult(aggregated)
        }
    }
}