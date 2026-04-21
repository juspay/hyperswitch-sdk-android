package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A payment widget that wraps the internal [PaymentWidgetView].
 *
 * Use this widget to embed a payment form in your application.
 */
open class HyperswitchElement @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val internalView: PaymentWidgetView = PaymentWidgetView(context, attrs, defStyleAttr)

    var type: String? = null

    init {
        addView(internalView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Initializes the widget with the given publishable key.
     * Registers an internal result handler that cleans up on completion.
     */
    fun initWidget(publishableKey: String) {
        internalView.initWidget(publishableKey)
        type?.let { internalView.setWidgetType(it) }
        internalView.onPaymentResult(PaymentResultListener { result ->
            if (result is PaymentResult.Completed) {
                internalView.removeWidget()
            }
        })
    }

    /**
     * Sets the SDK authorization token.
     */
    fun setSdkAuthorization(sdkAuthorization: String) {
        internalView.setSdkAuthorization(sdkAuthorization)
    }

    fun showWidget() {
        internalView.showWidgetInternal()
    }

    /**
     * Suspending variant — resumes with the result and cleans up on completion.
     */
    suspend fun confirmPayment(): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            internalView.confirmPayment { result ->
                if (result is PaymentResult.Completed) {
                    internalView.removeWidget()
                }
                continuation.resume(result)
            }
        }

    /**
     * Callback variant — caller is responsible for any post-result cleanup.
     */
    fun confirmPayment(callback: (PaymentResult) -> Unit) {
        internalView.confirmPayment(callback)
    }

    /**
     * Registers a result handler using PaymentResultListener.
     */
    fun onPaymentResult(listener: PaymentResultListener) {
        internalView.onPaymentResult(listener)
    }

    /**
     * Registers a result handler with a lambda. Widget is removed only on completion.
     */
    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        internalView.onPaymentResult(PaymentResultListener { result ->
            onResult(result)
            if (result is PaymentResult.Completed) {
                internalView.removeWidget()
            }
        })
    }

    /**
     * Suspending CVC confirmation.
     */
    suspend fun confirmCVCWidget(
        paymentToken: String,
        paymentMethodId: String
    ): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            internalView.confirmCvcPayment(paymentToken, paymentMethodId) { result ->
                continuation.resume(result)
            }
        }

    /**
     * Callback CVC confirmation.
     */
    fun confirmCVCWidget(
        paymentToken: String,
        paymentMethodId: String,
        callback: (PaymentResult) -> Unit
    ) {
        internalView.confirmCvcPayment(paymentToken, paymentMethodId, callback)
    }

    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        internalView.setConfiguration(configuration)
    }

    /** RN bridge path - sets configuration using ReadableMap */
    fun setConfiguration(configuration: ReadableMap) {
        internalView.setConfiguration(configuration)
    }

    fun updateIntentInit(onInitComplete: () -> Unit) {
        internalView.updatePaymentIntentInit(
            onInitComplete
        )
    }

    suspend fun updateIntentComplete(
        sdkAuthorization: String
    ): ElementUpdateIntentResult {
        return suspendCancellableCoroutine { continuation ->
            internalView.updatePaymentIntentComplete(sdkAuthorization) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }
}
