package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet

class GooglePayButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasePaymentWidget(context, attrs, defStyleAttr, 0, "google_pay")
