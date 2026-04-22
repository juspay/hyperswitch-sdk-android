package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet

open class CVCWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HyperswitchElement(context, attrs, defStyleAttr) {
    init {
        type = "cvcWidget"
    }
}