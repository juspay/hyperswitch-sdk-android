package io.hyperswitch.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.facebook.react.bridge.ReadableMap
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.paymentsheet.PaymentRequestData
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.sdk.PaymentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A payment widget that wraps the internal [PaymentWidgetView].
 *
 * Use this widget to embed a payment form in your application.
 */
open class HyperswitchElement @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val internalView: PaymentWidgetView = PaymentWidgetView(context, attrs, defStyleAttr)

    /** JS surface type; reaches the inner view even when the element is never bound to a session. */
    var type: String? = null
        set(value) {
            field = value
            internalView.setWidgetType(value)
        }

    init {
        addView(internalView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var heightFloorPx: Int = 0

    protected fun setHeightFloor(heightDp: Float) {
        heightFloorPx = (heightDp * resources.displayMetrics.density).toInt()
        minimumHeight = heightFloorPx
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (heightFloorPx > 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            internalView.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
            )
        }
    }

    /** The session this element pays for; its credentials are read at every confirm. */
    private var session: PaymentSession? = null

    /** Marks this element as part of a session so JS scopes session-wide events to it. */
    fun setSessionTag(tag: Int?) {
        internalView.setSessionTag(tag)
    }


    internal fun bind(session: PaymentSession) {
        // A widget already on screen belongs to the session it was started for: it shows that
        // session's intent and carries its tag, by which JS scopes updateIntent to it. Bound to
        // another session it starts over, or it would stay the old session's widget for good.
        if (this.session != null && this.session !== session) {
            internalView.removeWidget()
        }
        this.session = session
        internalView.setSessionTag(session.sessionTag)
    }

    /** Brings the widget's credentials up to date with the session before a confirm. */
    private fun syncCredentials() {
        session?.let { internalView.setSdkAuthorization(it.getSdkAuthorization()) }
    }

    private fun refusal(): PaymentResult? =
        if (session?.isUpdatingIntent == true) PaymentResult.Failed(
            Throwable("An intent update is in progress; confirm after it completes").apply {
                initCause(Throwable("UPDATE_IN_PROGRESS"))
            }
        ) else null

    /**
     * Initializes the widget with a full [HyperswitchBaseConfiguration].
     * Registers an internal result handler that cleans up on completion.
     */

    fun initWidget(config: HyperswitchBaseConfiguration) {
        internalView.initWidget(config)
        type?.let { internalView.setWidgetType(it) }
        internalView.onPaymentResult(PaymentResultListener { result ->
            if (result is PaymentResult.Completed) {
                internalView.removeWidget()
            }
        })
    }

    /**
     * Sets the SDK authorization token.
     */
    fun setSdkAuthorization(sdkAuthorization: String) {
        internalView.setSdkAuthorization(sdkAuthorization)
    }

    fun showWidget() {
        internalView.showWidgetInternal()
    }

    /**
     * Suspending variant — resumes with the result and cleans up on completion.
     */
    @JvmSynthetic
    suspend fun confirmPayment(): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            refusal()?.let { continuation.resume(it); return@suspendCancellableCoroutine }
            syncCredentials()
            internalView.confirmPayment { result ->
                if (result is PaymentResult.Completed) {
                    internalView.removeWidget()
                }
                continuation.resume(result)
            }
        }

    /**
     * Callback variant — caller is responsible for any post-result cleanup.
     */
    fun confirmPayment(callback: (PaymentResult) -> Unit) {
        refusal()?.let { return callback(it) }
        syncCredentials()
        internalView.confirmPayment(callback)
    }


    fun onPaymentConfirmButtonClick(callback: (data: PaymentRequestData?, onConfirmPaymentCallback: (Boolean) -> Unit) -> Unit){
        internalView.onPaymentConfirmButtonClick(callback)
    }

    /**
     * Registers a result handler using PaymentResultListener.
     */
    fun onPaymentResult(listener: PaymentResultListener) {
        internalView.onPaymentResult(listener)
    }

    /**
     * Registers a result handler with a lambda. Widget is removed only on completion.
     */
    fun onPaymentResult(onResult: (PaymentResult) -> Unit) {
        internalView.onPaymentResult(PaymentResultListener { result ->
            onResult(result)
            if (result is PaymentResult.Completed) {
                internalView.removeWidget()
            }
        })
    }

    /**
     * Suspending CVC confirmation.
     */
    @JvmSynthetic
    suspend fun confirmCVCWidget(
        sdkAuthorization: String,
        paymentToken: String,
        billing: String? = null
    ): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            internalView.confirmCvcPayment(sdkAuthorization, paymentToken, billing) { result ->
                continuation.resume(result)
            }
        }

    /**
     * Callback CVC confirmation.
     */
    fun confirmCVCWidget(
        sdkAuthorization: String,
        paymentToken: String,
        billing: String? = null,
        callback: (PaymentResult) -> Unit
    ) {
        internalView.confirmCvcPayment(sdkAuthorization, paymentToken, billing, callback)
    }

    /** Native path - sets configuration using PaymentSheet.Configuration */
    fun setConfiguration(configuration: PaymentSheet.Configuration) {
        internalView.setConfiguration(configuration)
    }

    /** RN bridge path - sets configuration using ReadableMap */
    fun setConfiguration(configuration: ReadableMap) {
        internalView.setConfiguration(configuration)
    }

    /**
     * Receives every event listed in the configuration's `subscriptionEvents`. Can be set at any
     * time; a payment element emits once bound, a CVC widget once attached.
     */
    fun onChange(listener: PaymentEventListener) {
        internalView.onChange(listener)
    }

    /** Fires once the element has finished loading (payment methods fetched); no subscription needed. Registered after that, it is called at once. */
    fun onReady(listener: Runnable) {
        internalView.onReady(listener)
    }

    /** Fires when focus enters the element (moving between its fields does not re-fire); no subscription needed. */
    fun onFocus(listener: Runnable) {
        internalView.onFocus(listener)
    }

    /** Fires when focus leaves the element entirely (moving between its fields does not fire); no subscription needed. */
    fun onBlur(listener: Runnable) {
        internalView.onBlur(listener)
    }

    @Deprecated("List events in configuration subscriptionEvents")
    fun setSubscribedEvents(events: List<String>) {
        internalView.setSubscribedEvents(events)
    }

    @Deprecated("Use onChange", ReplaceWith("onChange(listener)"))
    fun setOnEventCallback(listener: PaymentEventListener) {
        internalView.onEvent(listener)
    }

    /** Backs the deprecated subscribe builder on [io.hyperswitch.sdk.Elements.bind]. */
    internal fun setLegacySubscription(events: List<String>, listener: PaymentEventListener) {
        internalView.setSubscribedEvents(events)
        internalView.onEvent(listener)
    }

    fun destroy() {
        internalView.removeWidget()
    }
}
