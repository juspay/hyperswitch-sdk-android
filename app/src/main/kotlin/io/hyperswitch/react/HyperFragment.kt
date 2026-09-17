package io.hyperswitch.react

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.interfaces.fabric.ReactSurface
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.views.scroll.ReactHorizontalScrollView
import com.facebook.react.views.scroll.ReactScrollView
import com.proyecto26.inappbrowser.ChromeTabsDismissedEvent
import com.proyecto26.inappbrowser.ChromeTabsManagerActivity
import io.hyperswitch.PaymentEvent
import io.hyperswitch.PaymentEventListener
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.redirect.RedirectEvent
import io.hyperswitch.utils.ConversionUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.concurrent.ConcurrentHashMap


enum class CallbackType {
    PAYMENT_RESULT,
    CONFIRM_ACTION,
    CONFIRM_CVC_ACTION,
    PAYMENT_CONFIRM_BUTTON_CLICK
}


sealed class HyperCallback {
    class Payment(val fn: ((PaymentResult) -> Unit)) : HyperCallback()
    class ConfirmButtonTriggered(
        val callback: (data: String, onPaymentResultCallback: (Boolean) -> Unit) -> Unit,
    ) : HyperCallback()
}

/**
 * Owner of one visible surface (sheet, payment widget or CVC widget) on the
 * shared host. Its root view carries this fragment, so every JS call for the
 * surface's root tag resolves back here. The host's lifecycle follows the
 * Activity, not this fragment: a session's surfaces outlive the sheet, so
 * dismissing it must not put the host to sleep.
 */
class HyperFragment : Fragment() {

    /**
     * Instance-level registry. No companion object, no static map.
     * Keyed by [CallbackType] so each slot is independently replaceable.
     */
    private val callbacks = ConcurrentHashMap<CallbackType, HyperCallback>()

    private val rt: HyperReactRuntime
        get() = ReactNativeController.runtime

    private var hyperSurface: ReactSurface? = null

    /**
     * The props the surface renders, copied from the launch options so a command pushed
     * through them never lands in [getArguments]: a fragment the OS recreates must start
     * from the original props, not replay the last confirm.
     */
    private var liveLaunchOptions: Bundle? = null

    private val surfaceId: Int
        get() = currentSurfaceId()

    fun currentSurfaceId(): Int {
        val id = hyperSurface?.surfaceID ?: return -1
        return if (id > 0) id else -1
    }

    /** Listener for this surface's events; set by the launcher or the bound element. */
    private var paymentEventListener: PaymentEventListener? = null

    private var onExit: (() -> Unit)? = null

    fun setOnExit(callback: () -> Unit) {
        onExit = callback
    }

    fun setOnPaymentResult(callback: ((PaymentResult) -> Unit)) {
        callbacks[CallbackType.PAYMENT_RESULT] = HyperCallback.Payment(callback)
    }

    fun hasPaymentResultCallback(): Boolean = callbacks.containsKey(CallbackType.PAYMENT_RESULT)

    fun setOnPaymentConfirmButtonClick(callback: (data: String, onPaymentResultCallback: ((Boolean) -> Unit)) -> Unit) {
        callbacks[CallbackType.PAYMENT_CONFIRM_BUTTON_CLICK] = HyperCallback.ConfirmButtonTriggered(
            callback
        )
    }

    fun setOnEventCallback(listener: PaymentEventListener) {
        this.paymentEventListener = listener
    }

    private var confirmSequence = 0

    /**
     * Main thread. Re-renders this fragment's React root with [update] applied to its props;
     * React delivers new props whether the root has rendered yet or not, so a command sent
     * this way is never lost to timing. False when the fragment has no surface.
     */
    private fun pushProps(update: Bundle.() -> Unit): Boolean {
        val surface = hyperSurface as? ReactSurfaceImpl ?: return false
        val launchOptions = liveLaunchOptions ?: return false
        val props = launchOptions.getBundle("props") ?: return false
        props.update()
        surface.updateInitProps(launchOptions)
        return true
    }

    /**
     * Confirms through the widget's own React root: a `widgetConfirm` marker on its props.
     * JS answers through `exitWidgetPaymentsheet` for this root tag.
     */
    fun confirmPayment(callback: ((PaymentResult) -> Unit)) {
        UiThreadUtil.runOnUiThread {
            if (callbacks.containsKey(CallbackType.CONFIRM_ACTION)) {
                callback.invoke(PaymentResult.Failed(Throwable("Payment already in progress")))
                return@runOnUiThread
            }
            callbacks[CallbackType.CONFIRM_ACTION] = HyperCallback.Payment(callback)
            confirmSequence += 1
            val attempt = confirmSequence
            val pushed = pushProps { putBundle("widgetConfirm", Bundle().apply { putInt("attempt", attempt) }) }
            if (!pushed) {
                callbacks.remove(CallbackType.CONFIRM_ACTION)
                callback.invoke(PaymentResult.Failed(Throwable("React Context not ready")))
            }
        }
    }

    /**
     * PAYMENT_RESULT  → fires CONFIRM_ACTION if present, otherwise PAYMENT_RESULT.
     * CONFIRM_ACTION  → fires and removes CONFIRM_ACTION (one-shot resolve).
     */
    fun notifyResult(type: CallbackType, result: String) {
        try {
            when (type) {
                CallbackType.PAYMENT_RESULT -> {
                    val confirmCallback =
                        callbacks.remove(CallbackType.CONFIRM_ACTION) as? HyperCallback.Payment
                    val confirmCvcCallback =
                        callbacks.remove(CallbackType.CONFIRM_CVC_ACTION) as? HyperCallback.Payment

                    when {
                        confirmCallback != null -> {
                            val parsed = parsePaymentResult(result)
                            confirmCallback.fn.invoke(parsed)
                            onExit?.invoke()
                        }

                        confirmCvcCallback != null -> {
                            val parsed = parsePaymentResult(result)
                            confirmCvcCallback.fn.invoke(parsed)
                            onExit?.invoke()
                        }

                        else -> {
                            val parsed = parsePaymentResult(result)
                            (callbacks.remove(CallbackType.PAYMENT_RESULT) as? HyperCallback.Payment)
                                ?.fn?.invoke(parsed)
                            onExit?.invoke()
                        }
                    }
                }

                CallbackType.CONFIRM_ACTION -> {
                    val parsed = parsePaymentResult(result)
                    (callbacks.remove(CallbackType.CONFIRM_ACTION) as? HyperCallback.Payment)?.fn?.invoke(
                        parsed
                    )
                }

                CallbackType.CONFIRM_CVC_ACTION -> {
                    val parsed = parsePaymentResult(result)
                    (callbacks.remove(CallbackType.CONFIRM_CVC_ACTION) as? HyperCallback.Payment)?.fn?.invoke(
                        parsed
                    )
                }

                else -> Log.i("HyperFragment", "notifyResult: unhandled type $type")
            }
        } catch (e: Exception) {
            Log.e("HyperFragment", "Error in notifyResult", e)
        }
    }

    fun notifyConfirmButtonClicked(payload: String, callback: (Boolean) -> Unit) {
        val confirmTriggeredCallback =
            callbacks[CallbackType.PAYMENT_CONFIRM_BUTTON_CLICK] as HyperCallback.ConfirmButtonTriggered?
        if(confirmTriggeredCallback == null){
            callback.invoke(true)
        }else {
            confirmTriggeredCallback.callback.invoke(payload, callback)
        }
        callbacks.remove(CallbackType.CONFIRM_ACTION)
    }

    /**
     * Called directly on this instance for streaming widget lifecycle events.
     */
    fun notifyEvent(eventType: String, result: ReadableMap) {
        try {
            val listener = paymentEventListener ?: return
            val payload = ConversionUtils.readableMapToMap(result)
            listener.onPaymentEvent(PaymentEvent(type = eventType, payload = payload))
        } catch (e: Exception) {
            Log.e("HyperFragment", "Error in notifyEvent", e)
        }
    }


    /**
     * Confirms the saved method through the CVC widget's own React root: a `cvcConfirm`
     * request on its props. The widget has no session, so the credentials travel with it.
     * JS answers through `exitHeadless` for this root tag.
     */
    fun confirmCvcPayment(
        sdkAuthorization: String,
        paymentToken: String,
        billing: String?,
        callback: ((PaymentResult) -> Unit)
    ) {
        UiThreadUtil.runOnUiThread {
            // One confirm at a time per widget; the JS reply resolves the slot.
            val registered =
                callbacks.putIfAbsent(CallbackType.CONFIRM_CVC_ACTION, HyperCallback.Payment(callback)) == null
            if (!registered) {
                callback.invoke(PaymentResult.Failed(
                    Throwable("CVC payment already in progress for this widget").apply {
                        initCause(Throwable("ALREADY_IN_PROGRESS"))
                    }
                ))
                return@runOnUiThread
            }
            confirmSequence += 1
            val attempt = confirmSequence
            val pushed = pushProps {
                putBundle("cvcConfirm", Bundle().apply {
                    putInt("attempt", attempt)
                    putString("sdkAuthorization", sdkAuthorization)
                    putString("paymentToken", paymentToken)
                    billing?.let { putString("billing", it) }
                })
            }
            if (!pushed) {
                callbacks.remove(CallbackType.CONFIRM_CVC_ACTION)
                callback.invoke(PaymentResult.Failed(Throwable("cannot find the view")))
            }
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createPaymentResult(
        status: String,
        message: String,
        error: String? = null,
        type: String? = null
    ): ReadableMap = Arguments.createMap().apply {
        putString("status", status)
        putString("message", message)
        error?.let { putString("error", it) }
        type?.let { putString("type", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The OS can recreate this fragment after process death before the host app
        // initialised the SDK; initialize() is idempotent.
        activity?.application?.let(ReactNativeController::initialize)
        super.onCreate(savedInstanceState)
        rt.follow(requireActivity())
        registerEventBus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val componentName = arguments?.getString("arg_component_name") ?: "hyperSwitch"
        val launchOptions = arguments?.getBundle("arg_launch_options")?.let { original ->
            Bundle(original).apply { getBundle("props")?.let { putBundle("props", Bundle(it)) } }
        }
        liveLaunchOptions = launchOptions
        val surface = rt.reactHost
            .createSurface(requireActivity(), componentName, launchOptions)
        surface.view?.let { SurfaceOwners.attach(it, this) }
        hyperSurface = surface
        surface.start()
        return surface.view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val reactRootView = view as? ViewGroup ?: return
        var scrollFixScheduled = false
        reactRootView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (!scrollFixScheduled) {
                    scrollFixScheduled = true
                    view.post {
                        scrollFixScheduled = false
                        fixScrollInterception(reactRootView)
                    }
                }
            }

            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
    }

    override fun onDestroyView() {
        try {
            super.onDestroyView()
            hyperSurface?.view?.let { SurfaceOwners.attach(it, null) }
            hyperSurface?.stop()
            hyperSurface = null
            liveLaunchOptions = null
            callbacks.clear()
            onExit = null
            paymentEventListener = null
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            unRegisterEventBus()
            callbacks.clear()
            onExit = null
            paymentEventListener = null
        } catch (_: Exception) {
        }
    }

    // ── Scroll fix ────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun fixScrollInterception(root: ViewGroup) {
        root.isNestedScrollingEnabled = true
        findReactScrollViews(root).forEach { scrollView ->
            scrollView.isNestedScrollingEnabled = true
            scrollView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    private fun findReactScrollViews(root: ViewGroup): List<ViewGroup> {
        val result = mutableListOf<ViewGroup>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ReactScrollView || child is ReactHorizontalScrollView) result.add(child as ViewGroup)
            if (child is ViewGroup) result.addAll(findReactScrollViews(child))
        }
        return result
    }

    // ── EventBus ──────────────────────────────────────────────────────────────

    private fun registerEventBus() {
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    private fun unRegisterEventBus() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    }


    @Subscribe
    fun onEvent(event: RedirectEvent) {
        unRegisterEventBus()
        EventBus.getDefault()
            .post(ChromeTabsDismissedEvent(event.message, event.resultType, event.isError))
        startActivity(ChromeTabsManagerActivity.createDismissIntent(requireContext()))
    }

    /** Hardware back for this surface: JS decides, the host falls back to the Activity's handler. */
    fun onBackPressed(): Boolean = rt.reactHost.onBackPressed()

    // ─── Builder ──────────────────────────────────────────────────────────────

    class Builder {
        private var mComponentName: String? = null
        private var mLaunchOptions: Bundle? = null
        private var mFabricEnabled: Boolean = false

        fun setComponentName(componentName: String?) = apply { mComponentName = componentName }
        fun setLaunchOptions(launchOptions: Bundle?) = apply { mLaunchOptions = launchOptions }
        fun setFabricEnabled(fabricEnabled: Boolean) = apply { mFabricEnabled = fabricEnabled }

        fun build(): HyperFragment = HyperFragment().also { fragment ->
            fragment.arguments = Bundle().apply {
                putString("arg_component_name", mComponentName)
                putBundle("arg_launch_options", mLaunchOptions)
                putBoolean("arg_fabric_enabled", mFabricEnabled)
            }
        }
    }
}
