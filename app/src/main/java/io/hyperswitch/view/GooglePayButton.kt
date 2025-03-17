package io.hyperswitch.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.react.Utils

class GooglePayButton constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var ctx: Context? = null
    private var walletType: String = "google_pay"

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
    Utils.openReactView(context as AppCompatActivity, Bundle(), walletType, id)
}

    override fun onMeasure(width: Int, height: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY)
        )
    }
}