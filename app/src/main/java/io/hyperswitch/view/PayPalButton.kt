package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.react.Utils

class PayPalButton constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var ctx: Context? = null
    private var walletType: String = "paypal"

    constructor(context: Context?) : this(context!!) {
        ctx = context
        initView(context)
    }

    constructor(context: Context?, attrs: AttributeSet?) : this(
        context!!, attrs
    ) {
        ctx = context
        initView(context)
    }

    private fun initView(context: Context) {
        val map = mapOf("themes" to "Dark")
        Utils.openReactView(context as AppCompatActivity, map, walletType, id)
    }


    override fun onMeasure(width: Int, height: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY)
        )
    }
}