package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.paymentmethods.FieldConfiguration

/**
 * Card-CVC input field widget — its internal React view renders with
 * `type = "cardCVCInput"`.
 */
class CardCVCInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseRNViewInput(context, attrs, defStyleAttr) {

    constructor(context: Context, configuration: FieldConfiguration) : this(context) {
        setConfiguration(configuration)
    }

    override val type: String = "cardCVCInput"

    /** Field-specific helpers. */
    fun setCVCPlaceholder(placeholder: String) = setPlaceholder(placeholder)

    fun setMaxLength(maxLength: Int) = setConfigurationProp("maxLength", maxLength)
}
