package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.paymentmethods.FieldConfiguration

/**
 * Card-expiry input field widget — its internal React view renders with
 * `type = "cardExpiryInput"`.
 */
class CardExpiryInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseRNViewInput(context, attrs, defStyleAttr) {

    constructor(context: Context, configuration: FieldConfiguration) : this(context) {
        setConfiguration(configuration)
    }

    override val type: String = "cardExpiryInput"

    /** Field-specific helpers. */
    fun setExpiryPlaceholder(placeholder: String) = setPlaceholder(placeholder)

    fun setExpiryFormat(format: String) = setConfigurationProp("expiryFormat", format)
}
