package io.hyperswitch.payments.view

import android.app.Activity


class WidgetLauncher(
    activity: Activity,
    widgetResId: Int,
    walletType: String,
) {

    private var widgetRes: Int = 1
    private var walletTypeStr: String = ""

    init {
        widgetRes = widgetResId
        walletTypeStr = walletType
    }
}