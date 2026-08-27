package io.hyperswitch.vault.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.vault.core.FieldType

open class HyperswitchSSNEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseVaultFieldView(context, attrs, defStyleAttr) {
    override val fieldTypeName: String = "ssnInput"
    override val defaultFieldType: FieldType = FieldType.SSN

    init {
        if (fieldName.isBlank()) fieldName = FieldType.SSN.rawValue
    }
}
