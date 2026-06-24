package io.hyperswitch.view

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.BuildConfig
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.model.ElementUpdateIntentResult
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsheet.PaymentRequestData
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperFragmentManager
import io.hyperswitch.react.ReactNativeController

import kotlin.math.abs

/**
 * Sealed interface representing the configuration source for the payment widget.
 * Supports both native Android (PaymentSheet.Configuration) and React Native (ReadableMap) paths.
 */
sealed interface PaymentWidgetConfig {
    data class Native(val configuration: PaymentSheet.Configuration) : PaymentWidgetConfig
    data class ReactNative(val configuration: ReadableMap) : PaymentWidgetConfig
}

/**
 * Functional interface for listening to payment results.
 * Unified interface used by both native and React Native callers.
 */
fun interface PaymentResultListener {
    fun onPaymentResult(result: PaymentResult)
}

fun interface ConfirmPaymentClickListener {
    fun onConfirmPaymentCallback(data: String, onConfirmPaymentCallback: (Boolean) -> Unit)
}


/**
 * Extension function to convert PaymentSheet.Configuration to Map<String, Any>.
 * TODO: Fill in the actual mapping implementation.
 */

class PaymentWidgetView : FrameLayout {
    private var widgetConfig: PaymentWidgetConfig? = null
    private lateinit var launchOptions: LaunchOptions
    private var fragment: HyperFragment? = null
    private lateinit var mContext: Context
    private var sdkAuthorization: String = ""
    private var hsConfig: HyperswitchBaseConfiguration? = null

    private var resultListener: PaymentResultListener? = null

    private var confirmPaymentClickListener: ConfirmPaymentClickListener? = null
    private var subscribedEvents: List<String> = emptyList()

    private var onEventCallback: PaymentEventListener? = null
    private var activeLayoutChangeListener: View.OnLayoutChangeListener? = null
    private var widgetShown = false

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
//        initWidget( ?: "")
        // Auto-show widget if SDK authorization is already set
        if (!isSdkAuthorizationEmpty()) {
            post { showWidgetInternal() }
        }
    }

    private fun init(context: Context) {
        if (id == NO_ID) {
            id = generateViewId()
        }
        this.mContext = context
        launchOptions = LaunchOptions(context.applicationContext, BuildConfig.VERSION_NAME, hsConfig)
    }

    fun setFragment(fragment: HyperFragment) {
        this.fragment = fragment
    }

    fun getFragment(): HyperFragment? {
        return this.fragment
    }

    private var widgetType: String? = null

    fun initWidget(config: HyperswitchBaseConfiguration) {
        this.hsConfig = config
        this.widgetType = this.widgetType ?: "widgetPaymentSheet"
        launchOptions = LaunchOptions(mContext.applicationContext, BuildConfig.VERSION_NAME, config)
        ReactNativeController.initialize(mContext.applicationContext as Application)
    }

    fun isSdkAuthorizationEmpty(): Boolean {
        return this.sdkAuthorization.isEmpty()
    }

    fun setWidgetType(widgetType: String?) {
        this.widgetType = widgetType
    }


    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        widgetConfig = PaymentWidgetConfig.Native(configuration)
    }

    /** RN bridge path - sets configuration using ReadableMap */
    fun setConfiguration(configuration: ReadableMap) {
        widgetConfig = PaymentWidgetConfig.ReactNative(configuration)
    }

    /** Resolves the configuration to a Map<String, Any>? regardless of source */
    private fun resolveConfiguration(): Bundle? {
        return when (val c = widgetConfig) {
            is PaymentWidgetConfig.Native -> {
                c.configuration.bundle
            }

            is PaymentWidgetConfig.ReactNative -> {
                val configMap =
                    io.hyperswitch.utils.ConversionUtils.readableMapToMap(c.configuration as com.facebook.react.bridge.ReadableMap)
                this.launchOptions.toBundle(configMap)
            }

            null -> null
        }
    }

    /** Native / coroutine path - caller passes a PaymentResultListener */
    fun onPaymentResult(listener: PaymentResultListener) {
        resultListener = listener
    }

    /** RN bridge path - converts PaymentResult to ReadableMap before invoking Callback */
    fun onPaymentResult(callback: Callback) {
        resultListener = PaymentResultListener { result ->
            val args = Arguments.createMap()
            when (result) {
                is PaymentResult.Completed -> {
                    args.putString("status", "completed")
                    args.putString("data", result.data)
                }

                is PaymentResult.Failed -> {
                    args.putString("status", "failed")
                    args.putString("message", result.throwable.message)
                    args.putString("code", "")
                }

                is PaymentResult.Canceled -> {
                    args.putString("status", "cancelled")
                    args.putString("data", result.data)
                }
            }
            callback.invoke(args)
        }
    }

    /** Dispatches the result to the registered listener */
    private fun dispatchResult(result: PaymentResult) {
        resultListener?.onPaymentResult(result)
    }

    fun onPaymentConfirmButtonClick(
        callback: (
            data: PaymentRequestData?,
            onConfirmPaymentCallback: (Boolean) -> Unit
        ) -> Unit
    ) {
        confirmPaymentClickListener = ConfirmPaymentClickListener { data, onConfirmPaymentCallback ->
            callback(PaymentRequestData.parse(data), onConfirmPaymentCallback)
        }
    }

    fun onPaymentConfirmButtonClickWithMap(
        callback: (
            data: Map<String, Any?>,
            onConfirmPaymentCallback: (Boolean) -> Unit
        ) -> Unit
    ) {
        confirmPaymentClickListener = ConfirmPaymentClickListener { data, onConfirmPaymentCallback ->
            callback(PaymentRequestData.toMap(data), onConfirmPaymentCallback)
        }
    }

    fun onPaymentConfirmButtonClick(listener: ConfirmPaymentClickListener) {
        confirmPaymentClickListener = listener
    }

    private fun dispatchConfirmTriggered(
        data: String,
        onConfirmPaymentCallback: (Boolean) -> Unit
    ) {
        if(confirmPaymentClickListener == null){
            onConfirmPaymentCallback(true)
        }else {
            confirmPaymentClickListener?.onConfirmPaymentCallback(data, onConfirmPaymentCallback)
        }
    }

    fun onEvent(listener: PaymentEventListener) {
        this.onEventCallback = listener
        this.fragment?.setOnEventCallback(listener)
    }

    fun setSubscribedEvents(events: List<String>) {
        this.subscribedEvents = events
    }

    fun getLaunchOptions(): Bundle {
        return this.launchOptions.getBundle(
            configuration = resolveConfiguration(),
            type = widgetType,
            from = when (widgetConfig) {
                is PaymentWidgetConfig.Native -> "nativeWidget"
                is PaymentWidgetConfig.ReactNative -> "rn"
                null -> "nativeWidget"
            },
            sessionConfig = if (this.sdkAuthorization.isNotEmpty()) PaymentSessionConfiguration(this.sdkAuthorization) else null,
            subscribedEvents = this.subscribedEvents,
        )
    }

    fun confirmPayment(callback: (PaymentResult) -> Unit) {
        this.fragment?.confirmPayment(callback)
    }


    fun updatePaymentIntentInit(callback: () -> Unit) {
        if (isEligibleForUpdateIntent()) {
            this.fragment?.updatePaymentIntentInit(callback)
        } else {
            callback()
        }
    }

    fun triggerBack() {
        this.fragment?.triggerBack()
    }

    fun updatePaymentIntentComplete(
        sdkAuthorization: String,
        callback: (ElementUpdateIntentResult) -> Unit
    ) {
        if (isEligibleForUpdateIntent()) {
            sdkAuthorization?.takeIf { it.isNotEmpty() }?.let {
                this.sdkAuthorization = it
            }
            this.fragment?.updatePaymentIntentComplete(sdkAuthorization, callback)
                ?: callback(
                    ElementUpdateIntentResult.Failure(
                        Throwable("Fragment not attached").apply {
                            initCause(Throwable("FRAGMENT_NOT_ATTACHED"))
                        }
                    ))
        } else {
            callback(ElementUpdateIntentResult.Success)
        }
    }

    private fun isEligibleForUpdateIntent(): Boolean {
        when (widgetType) {
            "payment",
            "tabSheet",
            "buttonSheet",
            "widgetPaymentSheet",
            "widgetTabSheet",
            "widgetButtonSheet",
            "hostedCheckout",
            "google_pay",
            "paypal",
            "card",
            "paymentMethodsManagement",
            "headless",
            "expressCheckout" -> return true

            "cvcWidget" -> return false
            else -> return false
        }
    }

    fun confirmCvcPayment(
        sdkAuthorization: String,
        paymentToken: String,
        billing: String?,
        callback: (PaymentResult) -> Unit
    ) {
        this.fragment?.confirmCvcPayment(sdkAuthorization, paymentToken, billing, callback)
    }

    fun setSdkAuthorization(sdkAuthorization: String) {
        this.sdkAuthorization = sdkAuthorization
        // Auto-show widget if already attached to window.
        // Use post() to guarantee execution on the main thread — callers may
        // invoke this from a background thread (e.g. Java integration).
        if (isAttachedToWindow && !isSdkAuthorizationEmpty()) {
            post { showWidgetInternal() }
        }
    }

    fun showWidgetInternal() {
        if (this.isSdkAuthorizationEmpty()) return  // callers already guard; no need to retry
        if (widgetShown) return
        widgetShown = true
        val activity = context as? FragmentActivity ?: return

        if (activity.isFinishing || activity.isDestroyed) return

        val tag = "HyperPaymentSheet_${this.id}"
        HyperFragmentManager.cancelPending(tag)
        this.setFragment(
            HyperFragment.Builder().setComponentName("hyperSwitch")
                .setLaunchOptions(this.getLaunchOptions()).build()
        )

        val frameLayout = FrameLayout(activity).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        this.addView(frameLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val containerWidth = this.width
        val containerHeight = this.height
        frameLayout.post {
            frameLayout.measure(
                View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(containerHeight, View.MeasureSpec.EXACTLY)
            )
            frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)
            setupLayout(frameLayout, containerWidth, containerHeight)
            HyperFragmentManager.addOrReplace(
                activity = activity,
                container = frameLayout,
                fragment = this.getFragment() as Fragment,
                tag = tag,
                addToBackStack = false
            )

            frameLayout.post { this.getFragment()?.view?.requestLayout() }
        }
        this.fragment?.setOnPaymentResult(::dispatchResult)
        this.fragment?.setOnPaymentConfirmButtonClick(::dispatchConfirmTriggered)
        onEventCallback?.let { this.fragment?.setOnEventCallback(it) }
        this.fragment?.setOnExit {
            removeWidget()
        }
    }

    private fun setupLayout(view: View, width: Int, height: Int) {
        // Do an initial one-shot layout pass.
        manuallyLayoutChildren(view, width, height)

        // Re-layout only when the view's dimensions actually change, not every frame.
        // This prevents the continuous forced layout() calls that destabilise focus
        // in the embedded React Native TextInput (e.g. CVCWidget).
        val listener = View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newW = right - left
            val newH = bottom - top
            val oldW = oldRight - oldLeft
            val oldH = oldBottom - oldTop
            if (newW != oldW || newH != oldH) {
                manuallyLayoutChildren(v, newW, newH)
            }
        }
        view.addOnLayoutChangeListener(listener)
        activeLayoutChangeListener = listener
    }

    fun stopLayout() {
        activeLayoutChangeListener?.let { listener ->
            // We don't hold a reference to the view here, so we rely on removeWidget()
            // calling removeAllViews() which detaches the listener automatically.
            // Nulling the reference is sufficient to prevent leaks.
            activeLayoutChangeListener = null
        }
    }

    fun removeWidget() {
        try {
            this.cancelPendingInputEvents()
            stopLayout()
            val activity = context as? FragmentActivity ?: return
            val tag = "HyperPaymentSheet_${this.id}"
            HyperFragmentManager.remove(activity, tag)
            post {
                removeAllViews()
            }
            widgetShown = false
        } catch (_: Exception) {
            // Handle the errors
        }
    }

    private fun manuallyLayoutChildren(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private var startY = 0f
    private var startX = 0f

    /**
     * Never intercept touch events - let the fragment's ReactRootView handle them.
     * This prevents the parent RN ScrollView from stealing touches before the
     * inner ReactFragment's ReactRootView gets a chance to process them.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    /**
     * Dispatch touch events and coordinate with parent ScrollView.
     * For vertical scrolling, we request the parent to not intercept touches,
     * allowing the inner ReactScrollView (inside the fragment) to handle them.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                startX = ev.x
                // Do not disallow parent interception on DOWN — this was originally written
                // for a parent RN ScrollView, but in native embedding (e.g. WidgetActivity)
                // it prevents the outer Android ScrollView from ever scrolling.
                // Direction-based gating on MOVE below is sufficient.
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = abs(ev.y - startY)
                val dx = abs(ev.x - startX)
                parent?.requestDisallowInterceptTouchEvent(dy > dx)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
