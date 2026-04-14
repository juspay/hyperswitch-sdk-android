package io.hyperswitch

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.facebook.react.bridge.Callback
import io.hyperswitch.view.PaymentWidgetView

/**
 * A payment widget that wraps the internal [PaymentWidgetView].
 *
 * Use this widget to embed a payment form in your application.
 */
class PaymentWidget @JvmOverloads constructor(
    context: Context,
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


    fun showWidget(){
        internalView.showWidgetInternal()
    }

    /**
     * Confirms the payment with the given callback.
     */
    fun confirmPayment(callback: Callback) {
        internalView.confirmPayment(callback)
    }

    /**
     * Updates the payment intent initialization.
     */
    fun updateIntent(callback: ()-> String) {
        val internalCallback : () -> Unit = {

        }
//        internalView.updatePaymentIntentInit(internalCallback)
        val sdkAuthorization: String = callback()
//        internalView.updatePaymentIntentComplete(sdkAuthorization, internalCallback)
    }
}
