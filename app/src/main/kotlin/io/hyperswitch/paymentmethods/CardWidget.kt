package io.hyperswitch.paymentmethods

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.runtime.ReactSurfaceView
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.react.ReactNativeController
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.parsePaymentResult
import io.hyperswitch.sdk.PaymentSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * The payments side of a card form that pays: hyperswitch-client-core's CardWidget, a React
 * root on the payments host that draws nothing. It opens the form's hosted form with the
 * payment's vault (through HyperVaultRelayModule), and on each confirm tokenizes it, confirms
 * the payment and runs 3DS.
 *
 * Like a payment widget it is driven by a `widgetConfirm` attempt on its props and answers on
 * HyperModule's widget channels, which find it by its root tag. Its root joins the window as a
 * 1x1 view off screen: the bundle draws its tree only once SafeAreaProvider has measured
 * insets, which a view does only while attached.
 */
internal class CardWidget(
    private val activity: Activity,
    private val form: CardForm,
    hyperswitchConfiguration: HyperswitchBaseConfiguration,
    session: PaymentSession,
    locale: String?,
) {

    private val runtime = ReactNativeController.run {
        initialize(activity.application)
        runtime
    }

    private val launchOptions: Bundle =
        LaunchOptions(activity.applicationContext, BuildConfig.VERSION_NAME, hyperswitchConfiguration)
            .getBundle(
                configuration = Bundle(),
                type = WIDGET_TYPE,
                sessionConfig = PaymentSessionConfiguration(session.getSdkAuthorization()),
            )
            .apply {
                getBundle("props")?.let { props ->
                    props.putBundle("cardForm", Bundle().apply {
                        putString("formId", form.formId)
                        locale?.let { putString("locale", it) }
                    })
                    session.sessionTag?.let { props.getBundle("sdkParams")?.putInt("sessionTag", it) }
                }
            }

    private var surface: ReactSurfaceImpl? = null
    private var view: ReactSurfaceView? = null
    private var attempt = 0
    private var pending: ((PaymentResult) -> Unit)? = null

    init {
        runtime.follow(activity)

        val surface = ReactSurfaceImpl(activity, COMPONENT_NAME, launchOptions)
        val view = ReactSurfaceView(activity, surface).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            translationX = -9999f
            translationY = -9999f
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        SurfaceOwners.attach(view, this)
        surface.attachView(view)
        surface.attach(runtime.reactHost)
        activity.findViewById<ViewGroup>(android.R.id.content)?.addView(view)
        surface.start()
        this.surface = surface
        this.view = view
    }

    /** Pays once: a second confirm while one runs is refused at once. Main thread. */
    fun confirm(callback: (PaymentResult) -> Unit) {
        val surface = surface
        val props = launchOptions.getBundle("props")
        when {
            pending != null -> callback(failed("confirm_in_progress", "A payment is already in progress for this card form."))
            surface == null || props == null -> callback(failed("form_unavailable", "The card form was closed."))
            else -> {
                pending = callback
                attempt += 1
                props.putBundle("widgetConfirm", Bundle().apply { putInt("attempt", attempt) })
                surface.updateInitProps(launchOptions)
            }
        }
    }

    /** The widget's confirm outcome (HyperModule.notifyWidgetPaymentResult), as JSON. Main thread. */
    fun onResult(resultJson: String) {
        val callback = pending ?: return
        pending = null
        callback(parsePaymentResult(resultJson))
    }

    /** An event from the widget (HyperModule.emitPaymentEvent): only `error` concerns the form. */
    fun onEvent(eventType: String, payload: ReadableMap) {
        if (eventType != "error") return
        val message = if (payload.hasKey("message") && !payload.isNull("message")) payload.getString("message") else null
        form.onWidgetError(message ?: "The card form cannot pay.")
    }

    /** HyperVaultRelay: open the form with the payment's vault. Main thread. */
    fun openVaultForm(descriptorJson: String) {
        val descriptor = try {
            JSONObject(descriptorJson)
        } catch (_: Exception) {
            return
        }
        if (descriptor.optString("formId") != form.formId) return
        form.openForm(bundleOf(descriptor))
    }

    /** HyperVaultRelay: tokenize the form; [reply] gets its PM_COMMAND_RESULT payload as JSON. */
    fun tokenizeVaultForm(formId: String, reply: (String) -> Unit) {
        if (formId != form.formId) {
            reply(JSONObject(mapOf("ok" to false, "message" to "No card form \"$formId\" belongs to this widget.")).toString())
            return
        }
        form.tokenizeForWidget(reply)
    }

    /** Ends the widget; a payment still pending is answered as not knowing its outcome. */
    fun stop() {
        view?.let {
            SurfaceOwners.attach(it, null)
            (it.parent as? ViewGroup)?.removeView(it)
        }
        surface?.stop()
        surface?.detach()
        surface = null
        view = null
        pending?.let {
            pending = null
            it(failed("form_unavailable", "The card form was closed while a payment was in progress; check the payment's status."))
        }
    }

    private fun failed(code: String, message: String) =
        PaymentResult.Failed(Throwable(message).apply { initCause(Throwable(code)) })

    private fun bundleOf(json: JSONObject): Bundle = Bundle().apply {
        for (key in json.keys()) {
            when (val value = json.opt(key)) {
                null, JSONObject.NULL -> {}
                is JSONObject -> putBundle(key, bundleOf(value))
                is JSONArray -> putSerializable(key, ArrayList(List(value.length()) { value.opt(it) }))
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Number -> putDouble(key, value.toDouble())
                else -> putString(key, value.toString())
            }
        }
    }

    private companion object {
        const val COMPONENT_NAME = "hyperSwitch"
        const val WIDGET_TYPE = "card"
    }
}
