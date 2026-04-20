package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.utils.ConversionUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

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
     */
    fun initWidget(publishableKey: String) {
        internalView.initWidget(publishableKey)
        type?.let {
            internalView.setWidgetType(type)
        }
        this._onPaymentResult()
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

    suspend fun confirmPayment(): PaymentResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = { paymentResult: PaymentResult ->
                when (paymentResult) {
                    is PaymentResult.Completed -> {
                        internalView.removeWidget()
                    }
                    else -> {}
                }
                continuation.resume(paymentResult)
            }
            internalView.confirmPayment(callback)
        }
    }

    /**
     * Confirms the payment with the given callback.
     */
    fun confirmPayment(callback: (PaymentResult) -> Unit) {
        internalView.confirmPayment(callback)
    }

    fun _onPaymentResult() {
        internalView.onPaymentResult { result ->
            internalView.removeWidget()
        }
    }

    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        try {
            internalView.onPaymentResult { result ->
                onResult(result)
            }
        } catch (_: Exception) {
        } finally {
            internalView.removeWidget()
        }
    }

    /**
     * Updates the payment intent initialization.
     */
    fun updateIntent(
        sdkAuthorizationProvider: () -> String,
        onComplete: (PaymentResult) -> Unit
    ) {
        internalView.updatePaymentIntentInit { -> }
        val sdkAuthorization = sdkAuthorizationProvider()
        internalView.updatePaymentIntentComplete(sdkAuthorization) { result ->
            onComplete(result)
        }
    }
}
