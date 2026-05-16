package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import android.util.Log
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class Elements internal constructor(
    activity: Activity,
    config: HyperswitchBaseConfiguration?,
    sessionConfiguration: PaymentSessionConfiguration
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Fix 1: thread-safe list
    private val hsElements: CopyOnWriteArrayList<HyperswitchBoundElement> = CopyOnWriteArrayList()

    private val paymentSession = PaymentSession(
        activity,
        config = config,
        sessionConfig = sessionConfiguration
    ).also { it.initPaymentSession(sessionConfiguration.sdkAuthorization) }

    fun bind(
        element: HyperswitchElement,
        configuration: PaymentSheet.Configuration? = null,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
    ): HyperswitchBoundElement {
        val hsElement = HyperswitchBoundElement(paymentSession, element, configuration, subscribe)
        hsElements.add(hsElement)
        return hsElement
    }

    fun bind(
        element: HyperswitchElement,
        configurationMap: Map<String, Any?>,
        subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
    ): HyperswitchBoundElement {
        val hsElement = HyperswitchBoundElement(paymentSession, element, configurationMap, subscribe)
        hsElements.add(hsElement)
        return hsElement
    }

    fun unbind(boundElement: HyperswitchBoundElement) {
        hsElements.remove(boundElement)
    }

    private val updateIntentInProgress = AtomicBoolean(false)

    suspend fun updateIntent(completion: suspend () -> PaymentSessionConfiguration): ElementsUpdateResult {
        if (!updateIntentInProgress.compareAndSet(false, true)) {
            return ElementsUpdateResult.TotalFailure(
                IllegalStateException("updateIntent already in progress").apply {
                    initCause(Throwable("ALREADY_IN_PROGRESS"))
                }
            )
        }
        try {
            return computeUpdateIntent(hsElements.toList(), completion)
        } finally {
            updateIntentInProgress.set(false)
        }
    }

    fun updateIntent(
        completion: suspend () -> PaymentSessionConfiguration,
        onResult: (ElementsUpdateResult) -> Unit
    ) {
        scope.launch {
            onResult(updateIntent(completion))
        }
    }

    private suspend fun computeUpdateIntent(
        targets: List<HyperswitchBoundElement>,
        completion: suspend () -> PaymentSessionConfiguration
    ): ElementsUpdateResult {
        if (targets.isEmpty()) return ElementsUpdateResult.Success
        val initResults: List<Pair<HyperswitchBoundElement, Result<Unit>>> = coroutineScope {
            targets.map { hsElement ->
                async {
                    hsElement to runCatching<Unit> {
                        suspendCancellableCoroutine { continuation ->
                            hsElement.updateIntentInit {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val initSucceeded = initResults.filter { (_, r) -> r.isSuccess }.map { (e, _) -> e }
        val initFailed = initResults
            .filter { (_, r) -> r.isFailure }
            .associate { (e, r) -> e to (r.exceptionOrNull() ?: IllegalStateException("Init failed")) }

        if (initSucceeded.isEmpty()) {
            return ElementsUpdateResult.TotalFailure(
                cause = IllegalStateException("All ${targets.size} elements failed at init")
            )
        }

        val sdkAuthorization = try {
            completion().sdkAuthorization
        } catch (_: Exception) {
            ""
        }

        val completeResults: List<Pair<HyperswitchBoundElement, ElementUpdateIntentResult>> =
            coroutineScope {
                initSucceeded.map { hsElement ->
                    async {
                        hsElement to hsElement.updateIntentComplete(sdkAuthorization)
                    }
                }.awaitAll()
            }


        val succeeded = completeResults
            .filter { (_, result) ->
                result is ElementUpdateIntentResult.Success
            }
            .map { (element, _) -> element }

        val failed: Map<HyperswitchBoundElement, Throwable> = buildMap {
            putAll(initFailed)

            completeResults.forEach { (element, result) ->
                when (result) {
                    is ElementUpdateIntentResult.Failure -> {
                        put(element, result.cause)
                    }
                    ElementUpdateIntentResult.Cancelled -> {
                        put(element, CancellationException("Update cancelled"))
                    }
                    else -> Unit
                }
            }
        }

        if(sdkAuthorization.isNotEmpty()) {
            paymentSession.updateSdkAuthorization(sdkAuthorization)
        }
        return when {
            failed.isEmpty() -> ElementsUpdateResult.Success
            succeeded.isEmpty() -> ElementsUpdateResult.TotalFailure(
                cause = IllegalStateException("All ${targets.size} elements failed to update")
            )
            else -> ElementsUpdateResult.PartialFailure(succeeded = succeeded, failed = failed)
        }
    }
    
    fun getPaymentSession(): PaymentSession = this.paymentSession

    fun getCustomerSavedPaymentMethods(savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit)) {
        paymentSession.getCustomerSavedPaymentMethods { handler ->
            savedPaymentMethodCallback(handler)
        }
    }

    suspend fun getCustomerSavedPaymentMethods(): PaymentSessionHandler {
        return paymentSession.getCustomerSavedPaymentMethods()
    }
}