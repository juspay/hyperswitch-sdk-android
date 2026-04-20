package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.paymentsheet.PaymentSheetResult
import io.hyperswitch.utils.ConversionUtils
import org.json.JSONObject

/**
 * A payment widget that wraps the internal [PaymentWidgetView].
 *
 * Use this widget to embed a payment form in your application.
 */
open class HyperswitchElement @JvmOverloads constructor(
    context: Context,
    val type: String = "",
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val internalView: PaymentWidgetView = PaymentWidgetView(context, attrs, defStyleAttr)

    init {
        addView(internalView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Initializes the widget with the given publishable key.
     */
    fun initWidget(publishableKey: String) {
        internalView.initWidget(publishableKey)
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
     * Confirms the payment with the given callback.
     */
    fun confirmPayment(callback: Callback) {
        internalView.confirmPayment(callback)
    }

    fun onPaymentResult(onResult: (PaymentSheetResult) -> Unit) {
        internalView.onPaymentResult { result ->
            val parsed = result.getOrNull(0) as? ReadableMap
            val paymentResult = if (parsed != null) {
                val jsonObject = ConversionUtils.convertMapToJson(parsed)
                val status = jsonObject.optString("status")
                when (status) {
                    "cancelled" -> PaymentSheetResult.Canceled(status)
                    "failed", "requires_payment_method" -> {
                        val message = jsonObject.optString("message", status)
                        val code = jsonObject.optString("code")
                        val throwable = Throwable(message).apply {
                            initCause(Throwable(code))
                        }
                        PaymentSheetResult.Failed(throwable)
                    }
                    else -> PaymentSheetResult.Completed(status)
                }
            } else {
                PaymentSheetResult.Failed(Throwable("Invalid result"))
            }
            onResult(paymentResult)
        }
        internalView.removeWidget()
    }

    /**
     * Updates the payment intent initialization.
     */
    fun updateIntent(
        sdkAuthorizationProvider: () -> String,
        onComplete: (String) -> Unit
    ) {
        internalView.updatePaymentIntentInit { _ ->
        }
        val sdkAuthorization = sdkAuthorizationProvider()
        internalView.updatePaymentIntentComplete(sdkAuthorization) { result ->
            val resultString = result.firstOrNull()?.toString() ?: ""
            onComplete(resultString)
        }
    }
}
