package io.hyperswitch.sdk

import android.app.Activity
import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsession.SavedPaymentMethodsConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
    ).also { it.initPaymentSession(sessionConfiguration) }

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

    @JvmSynthetic
    suspend fun updateIntent(completion: suspend () -> PaymentSessionConfiguration): ElementsUpdateResult {
        if (!updateIntentInProgress.compareAndSet(false, true)) {
            return ElementsUpdateResult.TotalFailure(
                IllegalStateException("updateIntent already in progress").apply {
                    initCause(Throwable("ALREADY_IN_PROGRESS"))
                }
            )
        }
        try {
            return computeUpdateIntent(completion)
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

    // One round trip through the session's prefetch surface; elements are switched in JS.
    private suspend fun computeUpdateIntent(
        completion: suspend () -> PaymentSessionConfiguration
    ): ElementsUpdateResult {
        val result: Result<String> = suspendCancellableCoroutine { continuation ->
            paymentSession.updateIntent(
                authorizationProvider = { onAuthorization ->
                    scope.launch {
                        val auth = try { completion().sdkAuthorization } catch (_: Exception) { "" }
                        onAuthorization(auth)
                    }
                },
                onResult = { r -> if (continuation.isActive) continuation.resume(r) }
            )
        }
        return result.fold(
            onSuccess = { ElementsUpdateResult.Success },
            onFailure = { ElementsUpdateResult.TotalFailure(it) },
        )
    }

    fun getPaymentSession(): PaymentSession = this.paymentSession

    fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
        savedPaymentMethodCallback: ((PaymentSessionHandler) -> Unit),
    ) {
        paymentSession.getCustomerSavedPaymentMethods(configuration) { handler ->
            savedPaymentMethodCallback(handler)
        }
    }

    @JvmSynthetic
    suspend fun getCustomerSavedPaymentMethods(
        configuration: SavedPaymentMethodsConfiguration? = null,
    ): PaymentSessionHandler {
        return paymentSession.getCustomerSavedPaymentMethods(configuration)
    }
}