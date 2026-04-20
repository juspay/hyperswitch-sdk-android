package io.hyperswitch.view

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.Choreographer
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
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.EventCallback
import io.hyperswitch.react.HyperFragment
import io.hyperswitch.react.HyperFragmentManager
import io.hyperswitch.react.ReactNativeController

import kotlin.math.abs

class PaymentWidgetView : FrameLayout {
    private var configuration: ReadableMap? = null
    private lateinit var launchOptions: LaunchOptions
    private var fragment: HyperFragment? = null
    private lateinit var mContext: Context
    private var publishableKey: String? = null
    private var profileId: String? = null
    private var sdkAuthorization : String = ""

    private var callback:  ((PaymentResult) -> Unit)? = null

    private var onEventCallback: EventCallback? = null
    private val choreographerCallbacks = mutableMapOf<Int, Choreographer.FrameCallback>()

    constructor(context: Context?) : super(context!!) {
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
        initWidget(PaymentConfiguration.publishableKey() ?: "")
        // Auto-show widget if SDK authorization is already set
        if (!isSdkAuthorizationEmpty()) {
            showWidgetInternal()
        }
    }

    private fun init(context: Context) {
        this.mContext = context
        launchOptions = LaunchOptions(context.applicationContext, BuildConfig.VERSION_NAME)
        this.publishableKey = PaymentConfiguration.publishableKey()
    }

    fun setFragment(fragment: HyperFragment) {
        this.fragment = fragment
    }

    fun getFragment(): HyperFragment? {
        return this.fragment
    }

    fun getConfiguration(): ReadableMap? {
        return this.configuration
    }

    fun getSdkAuthorization(): String{
        return this.sdkAuthorization
    }
    private var widgetType: String? = null

//    fun setWidgetId(widgetId: String) {
//        this.widgetId = widgetId
//    }
//
//    fun getWidgetId() : String{
//        return this.widgetId;
//    }


    fun initWidget(publishableKey: String) {
        initWidget(publishableKey, this.profileId ?: "")
    }

    fun initWidget(
        publishableKey: String, profileId: String
    ) {
        initWidget(
            mContext.applicationContext as Application, this.widgetType ?: "widgetPaymentSheet", publishableKey, profileId
        )
    }

    fun initWidget(
        application: Application,
        type: String,
        publishableKey: String,
        profileId: String,
    ) {
        this.widgetType = type
        this.publishableKey = publishableKey
        this.profileId = profileId
        ReactNativeController.initialize(application)
    }


    fun isSdkAuthorizationEmpty(): Boolean {
        return this.sdkAuthorization.isEmpty()
    }

    fun setWidgetType(widgetType: String?) {
        this.widgetType = widgetType
    }


    fun configuration(configuration: ReadableMap) {
        this.configuration = configuration
    }

    fun onPaymentResult( callback: (PaymentResult) -> Unit) {
        this.callback = callback
    }

    fun onEvent(eventCallback: EventCallback) {
        this.onEventCallback = eventCallback
    }


    fun getLaunchOptions(): Bundle =
        this.launchOptions.getBundle(
            publishableKey = this.publishableKey,
            configuration = this.getConfiguration() as Map<String, Any>?,
            customBackendUrl = PaymentConfiguration.customBackendUrl,
            customLogUrl = PaymentConfiguration.customLogUrl,
            customParams = PaymentConfiguration.customParams as Map<String, Any>?,
            type = widgetType,
//            widgetId = this.widgetId,
            sdkAuthorization= this.sdkAuthorization,
        )

    fun confirmPayment(callback:  (PaymentResult) -> Unit) {
        this.fragment?.confirmPayment(callback)
    }
    fun updatePaymentIntentInit(callback:  () -> Unit){
        this.fragment?.updatePaymentIntentInit(callback)
    }

    fun updatePaymentIntentComplete(sdkAuthorization : String, callback:  (PaymentResult) -> Unit){
        this.fragment?.updatePaymentIntentComplete(sdkAuthorization, callback)
    }

    fun confirmCvcPayment(paymentToken: String, paymentMethodId: String, callback : (PaymentResult) -> Unit){
        this.fragment?.confirmCvcPayment( paymentToken, paymentMethodId, callback)
    }

    fun setSdkAuthorization(sdkAuthorization: String){
        this.sdkAuthorization = sdkAuthorization
        // Auto-show widget if already attached to window
        if (isAttachedToWindow && !isSdkAuthorizationEmpty()) {
            showWidgetInternal()
        }
    }

    fun showWidgetInternal() {
        if (this.isSdkAuthorizationEmpty()) {
            this.post { showWidgetInternal() }
            return
        }
        if(this.publishableKey == null) {
            this.initWidget(this.publishableKey ?: "")
        }
        val activity = context as? FragmentActivity

        activity?.let {
            if (activity.isFinishing || activity.isDestroyed) return

            val tag = "HyperPaymentSheet_${this.id}"
            HyperFragmentManager.cancelPending(tag)
            this.setFragment(
                HyperFragment.Builder().setComponentName("hyperSwitch")
                    .setLaunchOptions(this.getLaunchOptions()).build()
            )

            val frameLayout = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
            this.addView(frameLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            frameLayout.post {
                frameLayout.measure(
                    View.MeasureSpec.makeMeasureSpec(this.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(this.height, View.MeasureSpec.EXACTLY)
                )
                frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)
                setupLayout(frameLayout)
                HyperFragmentManager.addOrReplace(
                    activity = activity,
                    container = frameLayout,
                    fragment = this.getFragment() as Fragment,
                    tag = tag
                )

                frameLayout.post { this.getFragment()?.view?.requestLayout() }
            }
            callback?.let { originalCallback ->
                this.fragment?.setOnPaymentResult { result ->
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
                    originalCallback.invoke(result)
                }
            }
            onEventCallback?.let { it -> this.fragment?.setOnEventCallback(it) }
            this.fragment?.setOnExit {
                removeWidget()
            }
        }
    }

    private fun setupLayout(view: View) {
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                try {
                    if (view.isAttachedToWindow) {
                        manuallyLayoutChildren(view)
                        view.viewTreeObserver.dispatchOnGlobalLayout()
                        Choreographer.getInstance().postFrameCallback(this)
                    } else {
                        choreographerCallbacks.remove(view.id)
                    }
                }catch (_: Exception){

                }
            }
        }
        choreographerCallbacks[view.id] = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stopLayout() {
        choreographerCallbacks.remove(this.id)?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
    }

    fun removeWidget() {
        try {
            this.cancelPendingInputEvents()
            stopLayout()
            val activity = context  as FragmentActivity
//                (context as ThemedReactContext).reactApplicationContext.currentActivity as? FragmentActivity
            val tag = "HyperPaymentSheet_${this.id}"
            activity?.let { HyperFragmentManager.remove(it, tag) }
        } catch (_: Exception) {
            // Handle the errors
        }
    }

    private fun manuallyLayoutChildren(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.width, view.height)
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
                // Tell parent RN ScrollView to back off - let fragment handle it initially
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = abs(ev.y - startY)
                val dx = abs(ev.x - startX)
                if (dy > dx) {
                    // Vertical scroll - let fragment's inner ReactScrollView handle it
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else {
                    // Horizontal scroll - let parent decide
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}