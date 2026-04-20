package io.hyperswitch.react

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.facebook.react.ReactFragment
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactRootView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.views.scroll.ReactHorizontalScrollView
import com.facebook.react.views.scroll.ReactScrollView
import com.proyecto26.inappbrowser.ChromeTabsDismissedEvent
import com.proyecto26.inappbrowser.ChromeTabsManagerActivity
import io.hyperswitch.events.EventResult
import io.hyperswitch.paymentsheet.PaymentSheetResult
import io.hyperswitch.redirect.RedirectEvent
import io.hyperswitch.utils.ConversionUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.text.ifEmpty



enum class EventName {
    CONFIRM_PAYMENT_ACTION,
    CONFIRM_CVC_PAYMENT
}


typealias EventCallback = (EventResult) -> Unit

enum class CallbackType {
    PAYMENT_RESULT,
    CONFIRM_ACTION,
    CONFIRM_CVC_ACTION,
    ON_EVENT,
    UPDATE_INTENT_INIT,
    UPDATE_INTENT_COMPLETE
}


sealed class HyperCallback {
    class Payment(val fn: Callback) : HyperCallback()
    class Event(val fn: EventCallback) : HyperCallback()
    class UpdateIntent(val fn : Callback) : HyperCallback()
}

class HyperFragment : ReactFragment() {

    /**
     * Instance-level registry. No companion object, no static map.
     * Keyed by [CallbackType] so each slot is independently replaceable.
     */
    private val callbacks = ConcurrentHashMap<CallbackType, HyperCallback>()


    private var onExit : (() -> Unit)? = null

    fun setOnExit(callback: () -> Unit){
        onExit = callback
    }

    fun setOnPaymentResult(callback: Callback) {
        callbacks[CallbackType.PAYMENT_RESULT] = HyperCallback.Payment(callback)
    }

    fun setOnEventCallback(eventCallback: EventCallback) {
        callbacks[CallbackType.ON_EVENT] = HyperCallback.Event(eventCallback)
    }

    fun updatePaymentIntentInit(callback: Callback) {
        val rootTag = reactDelegate.reactRootView?.rootViewTag ?: -1
        if (rootTag == -1) {
            callback.invoke(
                createPaymentResult(
                    "error",
                    "React context not ready",
                    "REACT_CONTEXT_NOT_READY"
                )
            )
            return
        }
        callbacks[CallbackType.UPDATE_INTENT_INIT] = HyperCallback.UpdateIntent(callback)
        reactNativeHost.reactInstanceManager.currentReactContext
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("updateIntentInit", Arguments.createMap().apply {
                putInt("rootTag", rootTag)
            })
    }

    fun updatePaymentIntentComplete(sdkAuthorization: String, callback: Callback) {
        val rootTag = reactDelegate.reactRootView?.rootViewTag ?: -1
        if (rootTag == -1) {
            callback.invoke(
                createPaymentResult(
                    "error",
                    "React context not ready",
                    "REACT_CONTEXT_NOT_READY"
                )
            )
            return
        }
        if(callbacks[CallbackType.UPDATE_INTENT_COMPLETE] != null){
            callback.invoke(
                createPaymentResult(
                    "error",
                    "Update intent complete already in progress",
                    "ALREADY_IN_PROGRESS"
                )
            )
            return
        }
        callbacks[CallbackType.UPDATE_INTENT_COMPLETE] = HyperCallback.UpdateIntent(callback)
        reactNativeHost.reactInstanceManager.currentReactContext
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("updateIntentComplete", Arguments.createMap().apply {
                putString("sdkAuthorization", sdkAuthorization)
                putInt("rootTag", rootTag)
            })
    }

    fun confirmPayment(callback: Callback) {
        if (callbacks.containsKey(CallbackType.CONFIRM_ACTION)) {
            callback.invoke(
                createPaymentResult(
                    "error",
                    "Payment already in progress",
                    "ALREADY_IN_PROGRESS"
                )
            )
            return
        }
        val rootTag = reactDelegate.reactRootView?.rootViewTag ?: -1
        if (rootTag == -1) {
            callback.invoke(
                createPaymentResult(
                    "error",
                    "React context not ready",
                    "REACT_CONTEXT_NOT_READY"
                )
            )
            return
        }
        callbacks[CallbackType.CONFIRM_ACTION] = HyperCallback.Payment(callback)
        reactNativeHost.reactInstanceManager.currentReactContext
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("triggerWidgetAction", Arguments.createMap().apply {
                putString("actionType", EventName.CONFIRM_PAYMENT_ACTION.name)
                putInt("rootTag", rootTag)
            })
    }

    /**
     * Called directly on this instance by the native module after finding the
     * fragment via [UIManagerModule] + [androidx.fragment.app.FragmentManager.findFragment].
     *
     * PAYMENT_RESULT  → fires CONFIRM_ACTION if present, otherwise PAYMENT_RESULT.
     * CONFIRM_ACTION  → fires and removes CONFIRM_ACTION (one-shot resolve).
     */
    fun notifyResult(type: CallbackType, result: String) {
        val parsed = parseResult(result)
        try {
            when (type) {
                CallbackType.PAYMENT_RESULT -> {
                    val confirmCallback = callbacks.remove(CallbackType.CONFIRM_ACTION) as? HyperCallback.Payment
                    if (confirmCallback != null) {
                        confirmCallback.fn.invoke(parsed)
                        if(this.onExit != null){
                            this.onExit!!.invoke()
                        }
                    } else {
                        (callbacks[CallbackType.PAYMENT_RESULT] as? HyperCallback.Payment)?.fn?.invoke(parsed)
                    }
                }

                CallbackType.UPDATE_INTENT_INIT ->
                    (callbacks.remove(CallbackType.UPDATE_INTENT_INIT) as? HyperCallback.UpdateIntent)?.fn?.invoke(
                        parsed
                    )

                CallbackType.UPDATE_INTENT_COMPLETE ->
                    (callbacks.remove(CallbackType.UPDATE_INTENT_COMPLETE) as? HyperCallback.UpdateIntent)?.fn?.invoke(
                        parsed
                    )

                CallbackType.CONFIRM_ACTION -> {
                    (callbacks.remove(CallbackType.CONFIRM_ACTION) as? HyperCallback.Payment)?.fn?.invoke(
                        parsed
                    )
                }

                else -> Log.i("HyperFragment", "notifyResult: unhandled type $type")
            }
        } catch (e: Exception) {
            Log.e("HyperFragment", "Error in notifyResult", e)
        }
    }


    private fun parseResult(data: String) {
        val jsonObject = JSONObject(data)
        val result = when (val status = jsonObject.getString("status")) {
            "cancelled" -> PaymentSheetResult.Canceled(status)
            "failed", "requires_payment_method" -> {
                val message = jsonObject.getString("message")
                val throwable = Throwable(message.ifEmpty { status })
                throwable.initCause(Throwable(jsonObject.getString("code")))
                PaymentSheetResult.Failed(throwable)
            }

            else -> PaymentSheetResult.Completed(status)
        }
    }

    /**
     * Called directly on this instance for streaming widget lifecycle events.
     */
    fun notifyEvent(eventType: String, result: ReadableMap) {
        try {

            (callbacks[CallbackType.ON_EVENT] as? HyperCallback.Event)
                ?.fn?.invoke(EventResult(eventType, ConversionUtils.convertMapToJson(result)))
        } catch (e: Exception) {
            Log.e("HyperFragment", "Error in notifyEvent", e)
        }
    }


    fun confirmCvcPayment(callback: Callback, paymentToken: String, paymentMethodId: String) {
        if (callbacks.containsKey(CallbackType.CONFIRM_CVC_ACTION)) {
            val json = JSONObject()
            json.put("status", "error")
            json.put("message", "CVC payment already in progress")
            json.put("code", "ALREADY_IN_PROGRESS")
            callback.invoke(json.toString())
            return
        }
        val rootTag = view?.id ?: -1
        if (rootTag == -1) {
            callback.invoke("ERROR", "FAILED")
            return
        }

        callbacks[CallbackType.CONFIRM_CVC_ACTION] = HyperCallback.Payment(callback)

        // Wire ExitHeadlessCallBackManager so that when CvcWidget JS calls exitHeadless(result),
        // the result flows back to our callback → promise.resolve in the wrapper module.
//        ExitHeadlessCallBackManager.setCallback { result: HeadlessPaymentResult ->
//            callbacks.remove(CallbackType.CONFIRM_CVC_ACTION)
//            val json = JSONObject()
//            when (result) {
//                is HeadlessPaymentResult.Completed -> {
//                    if (result.data == "requires_customer_action") {
//                        json.put("status", "failed")
//                        json.put("code", "requires_customer_action")
//                        json.put("message", "Payment requires additional authentication")
//                    } else {
//                        json.put("status", "success")
//                        json.put("message", "Payment confirmed successfully")
//                        json.put("data", result.data)
//                    }
//                }
//
//                is HeadlessPaymentResult.Failed -> {
//                    json.put("status", "failed")
//                    json.put("code", result.throwable.cause?.message ?: "UNKNOWN_ERROR")
//                    json.put("message", result.throwable.message ?: "An error has occurred.")
//                }
//
//                is HeadlessPaymentResult.Canceled -> {
//                    json.put("status", "cancelled")
//                    json.put("message", "Payment confirmation cancelled")
//                    json.put("data", result.data)
//                }
//            }
//            callback.invoke(json.toString())
//        }


        val map = Arguments.createMap()
        map.putString("actionType", EventName.CONFIRM_CVC_PAYMENT.name)
        map.putInt("rootTag", rootTag)
        map.putString("paymentToken", paymentToken)
        map.putString("paymentMethodId", paymentMethodId)
        reactNativeHost.reactInstanceManager.currentReactContext
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("triggerWidgetAction", map)
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
        super.onCreate(savedInstanceState)
        registerEventBus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val reactRootView = view as? ReactRootView ?: return
        reactRootView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                view.post { fixScrollInterception(reactRootView) }
            }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
    }

    override fun onDestroyView() {
        (view as? ReactRootView)?.unmountReactApplication()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        unRegisterEventBus()
        callbacks.clear()
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

    fun unRegisterEventBus() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    }


    @Subscribe
    fun onEvent(event: RedirectEvent) {
        unRegisterEventBus()
        EventBus.getDefault().post(ChromeTabsDismissedEvent(event.message, event.resultType, event.isError))
        startActivity(ChromeTabsManagerActivity.createDismissIntent(requireContext()))
    }

    override fun getReactNativeHost(): ReactNativeHost = ReactNativeController.getReactNativeHost()
    override fun getReactHost(): ReactHost = ReactNativeController.getReactHost()

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