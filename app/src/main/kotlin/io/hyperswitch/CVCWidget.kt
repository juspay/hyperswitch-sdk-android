package io.hyperswitch

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.hyperswitch.paymentsession.Callback
import io.hyperswitch.view.PaymentWidgetView

/**
 * A CVC (Card Verification Code) widget that wraps the internal [PaymentWidgetView].
 *
 * Use this widget to collect CVC/CVV input for saved card payments.
 * The widget provides a secure input field for the 3 or 4 digit security code.
 */
class CVCWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val internalView: PaymentWidgetView

    init {
        internalView = PaymentWidgetView(context, attrs, defStyleAttr)
        internalView.setWidgetType("cvcWidget")
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

    /**
     * Confirms the CVC payment with the given payment token and payment method ID.
     *
     * @param callback The callback to receive the payment result
     * @param paymentToken The payment token for the saved card
     * @param paymentMethodId The payment method ID for the saved card
     */
    fun confirmCvcPayment(
        callback: Callback,
        paymentToken: String,
        paymentMethodId: String
    ) {
        internalView.confirmCvcPayment(callback as (Array<out Any?>) -> Unit, paymentToken, paymentMethodId)
    }
}
