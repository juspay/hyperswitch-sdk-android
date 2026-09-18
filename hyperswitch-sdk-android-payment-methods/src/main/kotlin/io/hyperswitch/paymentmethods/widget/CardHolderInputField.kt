package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.paymentmethods.FieldConfiguration

/**
 * Card-holder-name input field widget — its internal React view renders with
 * `type = "cardHolderInput"`.
 */
class CardHolderInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseRNViewInput(context, attrs, defStyleAttr) {

    constructor(context: Context, configuration: FieldConfiguration) : this(context) {
        setConfiguration(configuration)
    }

    override val type: String = "cardHolderInput"

    /** Field-specific helpers. */
    fun setCardHolderPlaceholder(placeholder: String) = setPlaceholder(placeholder)

    fun setCapitalization(enabled: Boolean) = setConfigurationProp("capitalization", enabled)
}
