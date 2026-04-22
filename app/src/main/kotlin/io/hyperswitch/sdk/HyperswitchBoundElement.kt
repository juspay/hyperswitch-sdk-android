package io.hyperswitch.sdk

import io.hyperswitch.PaymentEventSubscriptionBuilder
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsession.Callback
import io.hyperswitch.paymentsession.PMError
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import io.hyperswitch.view.PaymentResultListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class HyperswitchBoundElement internal constructor(
    private val paymentSession: PaymentSession,
    private val element: HyperswitchElement,
    configuration: PaymentSheet.Configuration? = null,
    subscribe: (PaymentEventSubscriptionBuilder.() -> Unit)? = null
) {
    private var lastUsedPaymentToken: String? = null
    private var lastUsedBilling: String? = null
    private var defaultPaymentToken: String? = null
    private var defaultBilling: String? = null

    init {
        element.initWidget(paymentSession.getPublishableKey())
        if (configuration != null) {
            element.setConfiguration(configuration)
        }
        if (subscribe != null) {
            val builder = PaymentEventSubscriptionBuilder()
            builder.subscribe()
            val (subscription, listener) = builder.build()
            element.setSubscribedEvents(subscription.getSubscribedEventStrings())
            element.setOnEventCallback(listener)
        }
        element.setSdkAuthorization(paymentSession.getSdkAuthorization())
        paymentSession.getCustomerSavedPaymentMethods { savedMethods ->
            savedMethods.getCustomerLastUsedPaymentMethodData().fold(
                onSuccess = { data ->
                    lastUsedPaymentToken = data.paymentToken
                    lastUsedBilling = data.billing
                },
                onFailure = { error ->
                }
            )
            // ── Default Saved ──
            savedMethods.getCustomerDefaultSavedPaymentMethodData().fold(
                onSuccess = { data ->
                    defaultPaymentToken = data.paymentToken
                    defaultBilling = data.billing
                },
                onFailure = { /* no default set — that's fine */ }
            )
        }
    }

    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        element.setConfiguration(configuration)
    }

//    /** RN bridge path - sets configuration using ReadableMap */
//    fun setConfiguration(configuration: ReadableMap) {
//        element.setConfiguration(configuration)
//    }

    //    /** Registers a PaymentResultListener */
    fun onPaymentResult(listener: PaymentResultListener) {
        element.onPaymentResult(listener)
    }

    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        element.onPaymentResult(onResult)
    }

    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult: PaymentResult ->
                continuation.resume(paymentResult)
            }
            element.confirmPayment(callback)
        }
    }

    fun confirmPayment(onResult: (PaymentResult) -> Unit) {
        element.confirmPayment(onResult)
    }

    suspend fun confirmWithLastUsed(): PaymentResult {
        return if (lastUsedPaymentToken != null) {
            element.confirmCVCWidget(paymentSession.getSdkAuthorization(), lastUsedPaymentToken ?: "", lastUsedBilling)
        } else {
            PaymentResult.Failed(Exception("No last used payment details found"))
        }
    }

    fun confirmWithLastUsed(callback: (PaymentResult) -> Unit) {
        if (lastUsedPaymentToken != null) {
            element.confirmCVCWidget(paymentSession.getSdkAuthorization(), lastUsedPaymentToken ?: "", lastUsedBilling, callback)
        } else {
            callback(PaymentResult.Failed(Exception("No last used payment details found")))
        }
    }

    suspend fun confirmWithDefaultMethod(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult: PaymentResult ->
                continuation.resume(paymentResult)
            }
            if (defaultPaymentToken != null) {
                element.confirmCVCWidget(paymentSession.getSdkAuthorization(), defaultPaymentToken ?: "", defaultBilling, callback)
            } else {
                continuation.resume(PaymentResult.Failed(Exception("No Default method found")))
            }
        }
    }

    fun confirmWithDefaultMethod(callback: (PaymentResult) -> Unit) {
        if (defaultPaymentToken != null) {
            element.confirmCVCWidget(paymentSession.getSdkAuthorization(), defaultPaymentToken ?: "", defaultBilling, callback)
        } else {
            callback(PaymentResult.Failed(Exception("No Default method found")))
        }
    }


    fun updateIntentInit(onInitComplete: () -> Unit) {
        element.updateIntentInit { onInitComplete() }
    }

    suspend fun updateIntentComplete(sdkAuthorization: String): ElementUpdateIntentResult {
        return element.updateIntentComplete(sdkAuthorization)
    }
}
