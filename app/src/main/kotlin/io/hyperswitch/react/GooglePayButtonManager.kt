package io.hyperswitch.react

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.google.android.gms.wallet.button.ButtonConstants
import io.hyperswitch.view.GooglePayButtonView
import com.facebook.react.viewmanagers.GooglePayViewManagerInterface
import com.facebook.react.viewmanagers.GooglePayViewManagerDelegate

@ReactModule(name = GooglePayButtonManager.REACT_CLASS)
class GooglePayButtonManager : SimpleViewManager<GooglePayButtonView>(), GooglePayViewManagerInterface<GooglePayButtonView> {
    private val delegate: GooglePayViewManagerDelegate<GooglePayButtonView, GooglePayButtonManager> =
        GooglePayViewManagerDelegate(this)
    override fun getName() = REACT_CLASS

    override fun getDelegate(): ViewManagerDelegate<GooglePayButtonView> = delegate

    override fun createViewInstance(context: ThemedReactContext) : GooglePayButtonView = GooglePayButtonView(context)

    override fun onAfterUpdateTransaction(view: GooglePayButtonView) {
        super.onAfterUpdateTransaction(view)
        view.addButton()
    }

    override fun setAllowedPaymentMethods(
        view: GooglePayButtonView,
        value: String?
    ) {
        value?.let {
            view.allowedPaymentMethods = value
        }
    }

    override fun setButtonType(
        view: GooglePayButtonView,
        type: String?
    ) {
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

    override fun setButtonStyle(
        view: GooglePayButtonView,
        theme: String?
    ) {
        view.theme = when (theme) {
            "light" -> ButtonConstants.ButtonTheme.LIGHT
            else -> ButtonConstants.ButtonTheme.DARK
        }
    }

    override fun setBorderRadius(
        view: GooglePayButtonView,
        radius: Double
    ) {
        view.cornerRadius = radius
    }

    companion object {
        const val REACT_CLASS = "GooglePayView"
    }
}
