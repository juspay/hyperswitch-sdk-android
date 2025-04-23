package io.hyperswitch.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.react.Utils



open class BasePaymentWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    widgetName: String = ""
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    init {
        openReact(widgetName)
    }

    private fun openReact(name: String) {
            Utils.openReactView(context as AppCompatActivity, Bundle(), name, id)
    }

    override fun onMeasure(width: Int, height: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY)
        )
    }

}
