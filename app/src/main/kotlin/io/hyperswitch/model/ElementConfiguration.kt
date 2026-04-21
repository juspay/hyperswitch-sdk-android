package io.hyperswitch.model

import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.view.HyperswitchElement
import io.hyperswitch.view.PaymentElement

data class ElementConfiguration(
    val element: HyperswitchElement,
    val configuration : PaymentSheet.Configuration? = null
)

