package io.hyperswitch.react

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.GooglePayButtonManagerDelegate
import com.facebook.react.viewmanagers.GooglePayButtonManagerInterface
import com.google.android.gms.wallet.button.ButtonConstants
import io.hyperswitch.view.GooglePayButtonView

class GooglePayButtonManager :
    SimpleViewManager<GooglePayButtonView>(),
    GooglePayButtonManagerInterface<GooglePayButtonView> {

    private val delegate: ViewManagerDelegate<GooglePayButtonView> =
        GooglePayButtonManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<GooglePayButtonView> = delegate

    override fun getName() = REACT_CLASS

    override fun createViewInstance(context: ThemedReactContext) = GooglePayButtonView(context)

    override fun onAfterUpdateTransaction(view: GooglePayButtonView) {
        super.onAfterUpdateTransaction(view)
        view.addButton()
    }

    @ReactProp(name = "allowedPaymentMethods")
    override fun setAllowedPaymentMethods(view: GooglePayButtonView, value: String?) {
        view.allowedPaymentMethods = value ?: return
    }

    @ReactProp(name = "buttonType")
    override fun setButtonType(view: GooglePayButtonView, value: String?) {
        view.type = when (value) {
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
    override fun setButtonStyle(view: GooglePayButtonView, value: String?) {
        view.theme = when (value) {
            "light" -> ButtonConstants.ButtonTheme.LIGHT
            else -> ButtonConstants.ButtonTheme.DARK
        }
    }

    @ReactProp(name = "borderRadius")
    override fun setBorderRadius(view: GooglePayButtonView, value: Float) {
        view.cornerRadius = value.toInt()
    }

    companion object {
        const val REACT_CLASS = "GooglePayButton"
    }
}
