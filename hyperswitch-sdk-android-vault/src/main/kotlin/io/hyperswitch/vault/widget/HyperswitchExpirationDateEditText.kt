package io.hyperswitch.vault.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.vault.core.FieldType

open class HyperswitchExpirationDateEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseVaultFieldView(context, attrs, defStyleAttr) {
    override val defaultFieldType: FieldType = FieldType.CARD_EXPIRATION_DATE

    init {
        if (fieldName.isBlank()) fieldName = FieldType.CARD_EXPIRATION_DATE.rawValue
    }
}
