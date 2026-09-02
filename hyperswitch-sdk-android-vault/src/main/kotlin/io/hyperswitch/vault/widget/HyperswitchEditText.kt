package io.hyperswitch.vault.widget

import android.content.Context
import android.util.AttributeSet
import io.hyperswitch.vault.core.FieldType

open class HyperswitchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseVaultFieldView(context, attrs, defStyleAttr) {
    override val defaultFieldType: FieldType = FieldType.INFO
}
