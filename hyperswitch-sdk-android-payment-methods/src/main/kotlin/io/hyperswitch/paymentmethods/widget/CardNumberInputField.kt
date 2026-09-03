package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.paymentmethods.FieldConfiguration

/**
 * Card-number input field widget — its internal React view renders with
 * `type = "cardNumberInput"`.
 *
 * ```kotlin
 * val cardNumberInput = findViewById<CardNumberInputField>(R.id.cardNumberInput)
 * cardNumberInput.setConfiguration(configuration)
 * cardForm.bind(cardNumberInput)
 * ```
 */
class CardNumberInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseRNViewInput(context, attrs, defStyleAttr) {

    constructor(context: Context, configuration: FieldConfiguration) : this(context) {
        setConfiguration(configuration)
    }

    override val type: String = "cardNumberInput"

    /** Field-specific helpers. */
    fun setCardNumberPlaceholder(placeholder: String) = setPlaceholder(placeholder)

    fun requestFocusOnBind(): CardNumberInputField = apply {
        setConfigurationProp("autoFocus", true)
    }
}
