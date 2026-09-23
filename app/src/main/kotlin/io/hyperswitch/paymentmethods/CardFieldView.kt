package io.hyperswitch.paymentmethods

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.runtime.ReactSurfaceView
import io.hyperswitch.paymentmethods.react.PaymentMethodsEventTarget
import io.hyperswitch.paymentmethods.react.PaymentMethodsProtocol
import io.hyperswitch.react.SurfaceOwners

/**
 * One field of a [CardForm]. Get one from the form and place it like any other view. What is
 * typed into it cannot be read back; [state] says whether it is complete and valid.
 */
@SuppressLint("ViewConstructor")
class CardFieldView internal constructor(
    context: Context,
    /** Kept so the form, and with it the card, lasts while any of its fields is in use. */
    private val form: CardForm,
    val elementType: CardElementType,
    options: CardFieldOptions,
) : FrameLayout(context) {

    var onReady: (() -> Unit)? = null
    var onChange: ((CardFieldState) -> Unit)? = null
    var onFocus: (() -> Unit)? = null
    var onBlur: (() -> Unit)? = null

    /** The latest snapshot, or null before the field has reported. */
    var state: CardFieldState? = null
        private set

    private var surface: ReactSurfaceImpl?

    /** What the bundle says about this field lands here. Held by the view, so it lasts as long. */
    private val events = PaymentMethodsEventTarget(::onEvent)
    private var measuredContentHeight = 0

    init {
        val props = options.toBundle().apply {
            putString("type", PaymentMethodsProtocol.FIELD_TYPE)
            putInt("protocolVersion", PaymentMethodsProtocol.VERSION)
            putString("formId", form.formId)
            putString("elementType", elementType.wire)
        }

        val surface = ReactSurfaceImpl(
            context,
            PaymentMethodsProtocol.FIELD_COMPONENT,
            Bundle().apply { putBundle("props", props) },
        )
        val view = ReactSurfaceView(context, surface)
        SurfaceOwners.attach(view, events)
        surface.attachView(view)
        surface.attach(form.runtime.reactHost)
        // A host that could not start is not retried from here: the form reports it.
        if (form.runtime.health.initFailure == null) surface.start()
        this.surface = surface

        /* The width is the app's to decide and the height is the field's. */
        addView(view, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /**
     * A parent sizing itself may measure with no width at all: a weighted row does, to find a
     * baseline. Passed on, that would lay the field out empty for a moment, and Android takes
     * focus away from a view that becomes empty, so the keyboard would close as it opened.
     * Such a pass is answered from the last real one and never reaches React.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || width == 0) {
            setMeasuredDimension(width, measuredContentHeight)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measuredContentHeight = measuredHeight
    }

    fun focus() = form.send("focus", elementType)

    fun blur() = form.send("blur", elementType)

    fun clear() = form.send("clear", elementType)

    /** Ends this field's screen. The form calls it when the form closes. */
    internal fun stop() {
        val surface = surface ?: return
        this.surface = null
        (getChildAt(0))?.let { SurfaceOwners.attach(it, null) }
        surface.stop()
        surface.detach()
        removeAllViews()
    }

    private fun onEvent(name: String, payload: Map<String, Any?>) {
        when (name) {
            PaymentMethodsProtocol.FIELD_READY -> onReady?.invoke()

            PaymentMethodsProtocol.FIELD_CHANGE -> {
                val next = CardFieldState.from(payload) ?: return
                if (next == state) return
                state = next
                onChange?.invoke(next)
            }

            PaymentMethodsProtocol.FIELD_FOCUS -> onFocus?.invoke()

            PaymentMethodsProtocol.FIELD_BLUR -> onBlur?.invoke()
        }
    }
}
