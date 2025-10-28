package io.hyperswitch.click_to_pay

import android.app.Activity

class ClickToPaySession internal constructor(private val clickToPaySessionLauncher: ClickToPaySessionLauncher){
    constructor(activity: Activity): this(
        DefaultClickToPaySessionLauncher(activity)
    )
}