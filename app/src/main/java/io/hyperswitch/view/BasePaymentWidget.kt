package io.hyperswitch.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.R
import io.hyperswitch.model.PaymentMethodCreateParams
import io.hyperswitch.react.Utils

open class BasePaymentWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    
    private var widgetHeight: Int = 150
    private var paymentMethod: String = ""
    fun getPaymentMethod(): String {
        return paymentMethod
    }
    init {
        // Read custom attributes
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.BasePaymentWidget,
                defStyleAttr,
                defStyleRes
            )

            try {
                paymentMethod = typedArray.getString(R.styleable.BasePaymentWidget_paymentMethod) ?: ""
//                widgetHeight = typedArray.getDimensionPixelSize(
//                    R.styleable.BasePaymentWidget_widgetHeight,
//                    DEFAULT_WIDGET_HEIGHT
//                )
            } finally {
                typedArray.recycle()
            }
        }

        // Initialize the widget with the specified payment method
        if (paymentMethod.isNotEmpty()) {
            openReact(paymentMethod)
        }
    }

    // Set payment method programmatically
    fun setPaymentMethod(method: String) {
        if (paymentMethod != method) {
            paymentMethod = method
            openReact(paymentMethod)
        }
    }

    val paymentMethodCreateParams: PaymentMethodCreateParams
        get() = PaymentMethodCreateParams()


    private fun openReact(name: String) {
        Utils.openReactView(context as AppCompatActivity, Bundle(), name, id)
    }

    fun setWidgetHeight(height: Int) {
        if (widgetHeight != height) {
            widgetHeight = height
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(widgetHeight, MeasureSpec.EXACTLY)
        )
    }
}
