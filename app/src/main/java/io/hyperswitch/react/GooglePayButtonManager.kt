package io.hyperswitch.react

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.google.android.gms.wallet.button.ButtonConstants
import io.hyperswitch.view.GooglePayButtonView

class GooglePayButtonManager : SimpleViewManager<GooglePayButtonView>() {
    override fun getName() = REACT_CLASS

    override fun createViewInstance(context: ThemedReactContext) = GooglePayButtonView(context)

    override fun onAfterUpdateTransaction(view: GooglePayButtonView) {
        super.onAfterUpdateTransaction(view)
        view.addButton()
    }

    @ReactProp(name = "allowedPaymentMethods")
    fun allowedPaymentMethods(view: GooglePayButtonView, allowedPaymentMethods: String) {
        view.allowedPaymentMethods = allowedPaymentMethods
    }

    @ReactProp(name = "buttonType")
    fun buttonType(view: GooglePayButtonView, type: String?) {
        view.type = when (type) {
            "BUY" -> ButtonConstants.ButtonType.BUY
            "BOOK" -> ButtonConstants.ButtonType.BOOK
            "CHECKOUT" -> ButtonConstants.ButtonType.CHECKOUT
            "DONATE" -> ButtonConstants.ButtonType.DONATE
            "ORDER" -> ButtonConstants.ButtonType.ORDER
            "PAY" -> ButtonConstants.ButtonType.PAY
            "SUBSCRIBE" -> ButtonConstants.ButtonType.SUBSCRIBE
            else -> ButtonConstants.ButtonType.PLAIN
        }
    }

    @ReactProp(name = "buttonStyle")
    fun buttonStyle(view: GooglePayButtonView, theme: String?) {
        view.theme = when (theme) {
            "light" -> ButtonConstants.ButtonTheme.LIGHT
            else -> ButtonConstants.ButtonTheme.DARK
        }
    }

    @ReactProp(name = "borderRadius")
    fun borderRadius(view: GooglePayButtonView, radius: Int) {
        view.cornerRadius = radius
    }

    companion object {
        const val REACT_CLASS = "GooglePayButton"
    }
}
