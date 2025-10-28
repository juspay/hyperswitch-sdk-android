package io.hyperswitch.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
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
 * [ReactNativeWidget] is instantiated.
 *
 *
 * The individual EditText views of this widget can be styled by defining a style
 * in `Configuration object`.
 *
 *
 * The card number, cvc, and expiry date will always be left to right regardless of locale.  Postal
 * code layout direction will be set according to the locale.
 */
class ReactNativeWidget : FrameLayout {
    constructor(context: Context?) : super(context!!) {
        initView(context)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        initView(context)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        initView(context)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context!!, attrs, defStyleAttr, defStyleRes
    ) {
        initView(context)
    }

    private fun initView(context: Context) {
        Utils.openReactView(context as AppCompatActivity, Bundle(), "card", id)
    }

    val paymentMethodCreateParams: PaymentMethodCreateParams
        get() = PaymentMethodCreateParams()

    override fun onMeasure(width: Int, height: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY)
        );
    }
}