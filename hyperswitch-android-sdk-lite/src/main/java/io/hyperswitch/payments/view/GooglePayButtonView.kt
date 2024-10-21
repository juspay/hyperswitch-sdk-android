package io.hyperswitch.payments.view

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.wallet.button.ButtonConstants
import com.google.android.gms.wallet.button.ButtonOptions
import com.google.android.gms.wallet.button.PayButton

open class GooglePayButtonView(private val context: Context) : FrameLayout(context) {

    lateinit var allowedPaymentMethods: String
    var type = ButtonConstants.ButtonType.PLAIN
    var theme = ButtonConstants.ButtonTheme.DARK
    var cornerRadius = 10
    private var button: PayButton? = null

    fun addButton() {
        if (button != null) {
            removeView(button)
        }
        button = initializeGooglePayButton()
        addView(button)
        viewTreeObserver.addOnGlobalLayoutListener { requestLayout() }
    }

    open fun toPixelFromDIP(value: Double): Float {
        return value.toFloat()
    }

    private fun initializeGooglePayButton(): PayButton {
        val googlePayButton = PayButton(context)

        val options = ButtonOptions.newBuilder()
            .setAllowedPaymentMethods(allowedPaymentMethods)
            .setButtonType(type)
            .setButtonTheme(theme)
            .setCornerRadius(toPixelFromDIP(this.cornerRadius.toDouble()).toInt())
        googlePayButton.initialize(options.build())
        googlePayButton.setOnClickListener { _ ->
            // Call the Javascript TouchableOpacity parent where the onClick handler is set
            (this.parent as? View)?.performClick() ?: run {
                Log.e("GooglePayReactNative", "Unable to find parent of GooglePayButtonView.")
            }
        }
        return googlePayButton
    }

    override fun requestLayout() {
        super.requestLayout()
        post(mLayoutRunnable)
    }

    private val mLayoutRunnable = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
    }
}
