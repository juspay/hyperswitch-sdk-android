package io.hyperswitch.vault.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.vault.core.FieldType

open class HyperswitchCardVerificationCodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseVaultFieldView(context, attrs, defStyleAttr) {
    override val fieldTypeName: String = "cvcInput"
    override val defaultFieldType: FieldType = FieldType.CARD_CVC

    init {
        if (fieldName.isBlank()) fieldName = FieldType.CARD_CVC.rawValue
    }
}
