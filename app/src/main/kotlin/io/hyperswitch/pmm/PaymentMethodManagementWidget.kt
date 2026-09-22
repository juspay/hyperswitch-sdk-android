package io.hyperswitch.pmm

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.runtime.ReactSurfaceView
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.pmm.react.PMMProtocol
import io.hyperswitch.pmm.react.PaymentMethodManagementEventTarget
import io.hyperswitch.pmm.react.PaymentMethodManagementRuntime
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.parsePaymentResult

/**
 * An embeddable PMM view: lists the customer's saved payment methods and takes new
 * cards, wherever and however tall the merchant's layout places it. It renders no
 * confirm CTA of its own — [tokenize], from a merchant-owned button, saves the card
 * being entered (web parity with `hyper.confirmTokenization`).
 */
@SuppressLint("ViewConstructor")
class PaymentMethodManagementWidget internal constructor(
    context: Context,
    props: Bundle,
) : FrameLayout(context) {

    private val runtime = PaymentMethodManagementRuntime.get(context.applicationContext as Application)
    private var surface: ReactSurfaceImpl? = null
    private var surfaceView: ReactSurfaceView? = null
    private var pendingTokenize: ((PaymentResult) -> Unit)? = null
    private var measuredContentHeight = 0

    /** What the bundle says about this root lands here. Held by the view, so it lasts as long. */
    private val events = object : PaymentMethodManagementEventTarget {
        override fun onPmmExit(resultJson: String, reset: Boolean) {
            resolveTokenize(parsePaymentResult(resultJson))
        }

        /* The save was refused (e.g. form validation): the surface stays mounted for retry. */
        override fun onPmmNonTerminalResult(result: PaymentResult) {
            resolveTokenize(result)
        }

        override fun onPmmEvent(eventType: String, payload: Map<String, Any?>) = Unit
    }

    init {
        val surface = ReactSurfaceImpl(context, PMMProtocol.COMPONENT, props)
        val view = ReactSurfaceView(context, surface)
        SurfaceOwners.attach(view, events)
        surface.attachView(view)
        surface.attach(runtime.reactHost)
        this.surface = surface
        this.surfaceView = view

        /* The width is the merchant's to decide and the height is the widget's. */
        addView(view, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        surface.start()
    }

    /**
     * Saves the card being entered (web parity with `hyper.confirmTokenization`).
     * [onResult] fires once: with the save's outcome, or with its validation
     * refusal — the widget stays mounted for retry either way.
     */
    fun tokenize(onResult: (PaymentResult) -> Unit) {
        if (pendingTokenize != null) {
            onResult(
                PaymentResult.Failed(Throwable("A tokenize is already in progress").apply { initCause(Throwable("TOKENIZE_IN_PROGRESS")) })
            )
            return
        }
        val tag = surfaceView?.rootViewTag ?: 0
        if (tag <= 0) {
            onResult(notReady())
            return
        }
        pendingTokenize = onResult
        val emitted = runtime.emitTriggerWidgetAction(Arguments.createMap().apply {
            putInt("rootTag", tag)
            putString("actionType", PMMProtocol.CONFIRM_ACTION)
        })
        if (!emitted) {
            pendingTokenize = null
            onResult(notReady())
        }
    }

    private fun resolveTokenize(result: PaymentResult) {
        val callback = pendingTokenize ?: return
        pendingTokenize = null
        callback(result)
    }

    private fun notReady(): PaymentResult =
        PaymentResult.Failed(Throwable("PMM view is not ready").apply { initCause(Throwable("SURFACE_NOT_READY")) })

    /**
     * A parent sizing itself may measure with no width at all: a weighted row does, to
     * find a baseline. Passed on, that would lay the widget out empty for a moment, and
     * Android takes focus away from a view that becomes empty, so the keyboard would
     * close as it opened. Such a pass is answered from the last real one and never
     * reaches React.
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

    /** The merchant drops the view; a tokenize still waiting is failed, not left hanging. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resolveTokenize(
            PaymentResult.Failed(Throwable("PMM view was removed").apply { initCause(Throwable("SURFACE_DETACHED")) })
        )
        val surface = surface ?: return
        this.surface = null
        surfaceView?.let { SurfaceOwners.attach(it, null) }
        surfaceView = null
        surface.stop()
        surface.detach()
        removeAllViews()
    }
}
