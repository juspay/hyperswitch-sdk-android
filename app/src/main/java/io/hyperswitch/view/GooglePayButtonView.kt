package io.hyperswitch.view

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.google.android.gms.wallet.button.ButtonConstants
import com.google.android.gms.wallet.button.ButtonOptions
import com.google.android.gms.wallet.button.PayButton
import com.facebook.react.bridge.ReactContext

class GooglePayButtonView(private val context: ThemedReactContext) : FrameLayout(context) {
    
    lateinit var allowedPaymentMethods: String
    var type = ButtonConstants.ButtonType.PLAIN
    var theme = ButtonConstants.ButtonTheme.DARK
    var cornerRadius = 10
    private var button: View? = null

    private fun sendInitEvent(isAvailable: Boolean) {
        val reactContext = context as? ReactContext ?: return
        val event = Arguments.createMap().apply {
            putBoolean("isAvailable", isAvailable)
        }
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("googlePayInit", event)
    }

    private var isGooglePayAvailable = false

    fun addButton() {
        if (button != null) {
            removeView(button)
        }
        button = initializeGooglePayButton()
        addView(button)
        viewTreeObserver.addOnGlobalLayoutListener { requestLayout() }
        
        sendInitEvent(isGooglePayAvailable)
    }

    private fun initializeGooglePayButton(): View {
       try {
           val googlePayButton = PayButton(context)

           val options = ButtonOptions.newBuilder()
               .setAllowedPaymentMethods(allowedPaymentMethods)
               .setButtonType(type)
               .setButtonTheme(theme)
               .setCornerRadius(PixelUtil.toPixelFromDIP(this.cornerRadius.toDouble()).toInt())

           googlePayButton.initialize(options.build())

           googlePayButton.setOnClickListener { _ ->
               (this.parent as? View)?.performClick() ?: run {
                   Log.e("GooglePayReactNative", "Unable to find parent of GooglePayButtonView.")
               }
           }
           isGooglePayAvailable = true
           return googlePayButton
      }
       catch (e: Exception) {
           Log.e("GooglePayButtonView", "Error initializing Google Pay button", e)

           isGooglePayAvailable = false
           return View(context).apply {
               visibility = View.GONE
               layoutParams = FrameLayout.LayoutParams(0, 0)
           }
       }
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