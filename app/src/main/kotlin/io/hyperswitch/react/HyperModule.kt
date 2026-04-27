package io.hyperswitch.react

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.IllegalViewOperationException
import com.facebook.react.uimanager.UIManagerModule
import io.hyperswitch.BuildConfig
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentEventSubscription
import io.hyperswitch.payments.GooglePayCallbackManager
import io.hyperswitch.payments.PazeCallbackManager
import io.hyperswitch.payments.view.WidgetLauncher
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PaymentSheetCallbackManager
import io.hyperswitch.payments.launcher.PaymentMethod
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class HyperModule internal constructor(private val rct: ReactApplicationContext) :
    ReactContextBaseJavaModule(rct) {
    companion object {
        // Static methods with unique signatures for reflection access from lite SDK
        @JvmStatic
        fun confirmStatic(tag: String, map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmStatic(tag, map)
        }

        @JvmStatic
        fun confirmCardStatic(map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmCardStatic(map)
        }

        @JvmStatic
        fun confirmECStatic(map: MutableMap<String, String?>) {
            HyperEventEmitter.confirmECStatic(map)
        }
    }

    override fun getName(): String {
        HyperEventEmitter.initialize(rct)
        return "HyperModule"
    }

    // Using invalidate instead of deprecated onCatalystInstanceDestroy
    override fun invalidate() {
        super.invalidate()
        HyperEventEmitter.deinitialize()
    }

    @ReactMethod
    fun updateWidgetHeight(height: Int) {
        // Express checkout widget height adjustment is not yet implemented.
    }

    @ReactMethod
    fun sendMessageToNative(rnMessage: String) {
        val jsonObject = JSONObject(rnMessage)
        if (jsonObject.optBoolean("isReady", false)) {
//            HyperEventEmitter.initialize(rct)
            WidgetLauncher.onPaymentReadyCallback(true)
        }
    }

    // Method to launch Google Pay payment
    @ReactMethod
    fun launchGPay(googlePayRequest: String, callBack: Callback) {
        currentActivity?.let {
            GooglePayCallbackManager.setCallback(
                it,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                it, BuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        } ?: run {
            GooglePayCallbackManager.setCallback(
                reactApplicationContext,
                googlePayRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                reactApplicationContext, BuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        }
    }

    // Method to launch Paze payment
    @ReactMethod
    fun launchPaze(pazeRequest: String, callBack: Callback) {
        currentActivity?.let {
            PazeCallbackManager.setCallback(
                it,
                pazeRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                it, BuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        } ?: run {
            PazeCallbackManager.setCallback(
                reactApplicationContext,
                pazeRequest,
                fun(data: Map<String, Any?>) {
                    callBack.invoke(
                        Arguments.fromBundle(
                            LaunchOptions(
                                reactApplicationContext, BuildConfig.VERSION_NAME
                            ).toBundle(data)
                        )
                    )
                },
            )
        }
    }

    // Method to exit the payment sheet
    @ReactMethod
    fun exitPaymentsheet(rootTag: Int, paymentResult: String, reset: Boolean) {
        val isFragment = PaymentSheetCallbackManager.executeCallback(paymentResult)
        (currentActivity as? FragmentActivity)?.let {
            if (isFragment) it.supportFragmentManager.findFragmentByTag("paymentSheet")
                ?.let { fragment ->
                    it.supportFragmentManager.beginTransaction().hide(fragment)
                        .commitAllowingStateLoss()
                }
            else it.finish()
        }
    }

    // Method to exit the widget
    @ReactMethod
    fun exitWidget(paymentResult: String, widgetType: String) {
        WidgetLauncher.onPaymentResultCallback(widgetType, paymentResult)
    }

    // Method to exit the card form
    @ReactMethod
    fun exitCardForm(paymentResult: String) {
        WidgetLauncher.onPaymentResultCallback(PaymentMethod.CARD.apiValue, paymentResult)
    }

    // Method to launch widget payment sheet
    @ReactMethod
    fun launchWidgetPaymentSheet(paymentResult: String, callBack: Callback) {
    }

    // Method to exit widget payment sheet
    @ReactMethod
    fun exitWidgetPaymentsheet(rootTag: Double, paymentResult: String, reset: Boolean) {
        findFragmentWithRootTag(rootTag.toInt(), {
            it?.notifyResult(CallbackType.PAYMENT_RESULT, paymentResult)
        })
    }

    @ReactMethod
    fun notifyWidgetPaymentResult(rootTag: Double, result: String) {
        findFragmentWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "notifyWidgetPaymentResult: no fragment found for rootTag=$rootTag")
            } else {
                fragment.notifyResult(CallbackType.CONFIRM_ACTION, result)
            }
        })
    }

    @ReactMethod
    fun onUpdateIntentEvent(rootTag: Double, type: String, result: String) {
        findFragmentWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "onUpdateIntentEvent: no fragment found for rootTag=$rootTag")
                return@findFragmentWithRootTag
            }
            if (type == "UPDATE_INTENT_INIT_RETURNED") {
                fragment.notifyResult(CallbackType.UPDATE_INTENT_INIT, result)
            } else if (type == "UPDATE_INTENT_COMPLETE_RETURNED") {
                fragment.notifyResult(CallbackType.UPDATE_INTENT_COMPLETE, result)
            }
        })
    }
    // Variable to keep track of event listener count
    private val listenerCount = AtomicInteger(0)


    // Method to add event listener
    @ReactMethod
    fun addListener(eventName: String?) {
        if (listenerCount.incrementAndGet() == 1) {
            HyperEventEmitter.initialize(rct)
        }
    }

// Method to remove event listeners
    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount.addAndGet(-count)
    }

    @ReactMethod
    fun emitPaymentEvent(rootTag: Double, eventType: String, payload: ReadableMap) {
        findFragmentWithRootTag(rootTag.toInt(), { fragment ->
            if (fragment == null) {
                Log.w("HyperModule", "emitPaymentEvent: no fragment found for rootTag=$rootTag")
            } else {
                fragment.notifyEvent(eventType, payload)
            }
        })
    }

    private fun findFragmentWithRootTag(rootTag: Int, onFound: (HyperFragment?) -> Unit) {
        val uiManagerModule =
            reactApplicationContext.getNativeModule<UIManagerModule?>(UIManagerModule::class.java)

        if (uiManagerModule == null) {
            onFound(null)
            return
        }

        uiManagerModule.addUIBlock { nvhm ->
            try {
                val reactRootView = nvhm.resolveView(rootTag)
                onFound(FragmentManager.findFragment(reactRootView))
            } catch (e: IllegalViewOperationException) {
                onFound(null)
            } catch (e: Exception) {
                onFound(null)
            }
        }
    }
}
