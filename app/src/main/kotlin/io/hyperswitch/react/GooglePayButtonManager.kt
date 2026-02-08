package io.hyperswitch.react

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.GooglePayButtonManagerDelegate
import com.facebook.react.viewmanagers.GooglePayButtonManagerInterface
import com.google.android.gms.wallet.button.ButtonConstants
import java.math.BigInteger

@ReactModule(name = GooglePayButtonViewManager.NAME)
class GooglePayButtonViewManager : SimpleViewManager<GooglePayButtonView>(),
    GooglePayButtonManagerInterface<GooglePayButtonView> {
    private val mDelegate: ViewManagerDelegate<GooglePayButtonView> =
        GooglePayButtonManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<GooglePayButton> {
        return mDelegate
    }

    override fun getName(): String {
        return NAME
    }

    public override fun createViewInstance(context: ThemedReactContext): GooglePayButtonView {
        return GooglePayButtonView(context)
    }

    public override fun onAfterUpdateTransaction(view: GooglePayButtonView) {
        super.onAfterUpdateTransaction(view)
        view.addButton()
    }

    override fun setButtonType(
        view: GooglePayButtonView?, value: String?
    ) {
        view?.type = when (value) {
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
        view: GooglePayButtonView?, value: String?
    ) {
        view?.theme = when (value) {
            "light" -> ButtonConstants.ButtonTheme.LIGHT
            else -> ButtonConstants.ButtonTheme.DARK
        }
    }

    override fun setBorderRadius(
        view: GooglePayButtonView?, value: Double
    ) {
        view?.cornerRadius = value.toInt()
    }

    override fun setAllowedPaymentMethods(
        view: GooglePayButtonView?, value: String?
    ) {
        if (value != null) {
            view?.allowedPaymentMethods = value
        }
    }

    companion object {
        const val NAME = "GooglePayButton"
    }
}
