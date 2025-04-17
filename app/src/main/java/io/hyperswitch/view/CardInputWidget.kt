package io.hyperswitch.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.model.PaymentMethodCreateParams
import io.hyperswitch.react.Utils

/**
 *
 *
 * A single-line card input widget.
 *
 *
 * To enable 19-digit card support, [io.hyperswitch.PaymentConfiguration] must be called before
 * [CardInputWidget] is instantiated.
 *
 *
 * The individual EditText views of this widget can be styled by defining a style
 * in `Configuration object`.
 *
 *
 * The card number, cvc, and expiry date will always be left to right regardless of locale.  Postal
 * code layout direction will be set according to the locale.
 */

class CardInputWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : BasePaymentWidget(context, attrs, defStyleAttr, defStyleRes, "card") {

    val paymentMethodCreateParams: PaymentMethodCreateParams
        get() = PaymentMethodCreateParams()
}

