package io.hyperswitch.view

import android.content.Context
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.payments.view.GooglePayButtonView

class GooglePayButtonView(private val context: Context) : GooglePayButtonView(context) {
    override fun toPixelFromDIP(value: Double): Float {
        return PixelUtil.toPixelFromSP(value)
    }
}