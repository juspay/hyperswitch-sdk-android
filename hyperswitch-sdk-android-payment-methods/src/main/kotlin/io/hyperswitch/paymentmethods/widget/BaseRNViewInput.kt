package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.interfaces.fabric.ReactSurface
import io.hyperswitch.paymentmethods.BundleUtils
import io.hyperswitch.paymentmethods.FieldConfiguration
import io.hyperswitch.paymentmethods.PaymentMethodSession

/**
 * Base class for every React-Native-backed payment-method input widget
 * (`BaseRNInputClass`).
 *
 * Concrete fields ([CardNumberInputField], [CardExpiryInputField], [CardCVCInputField],
 * [CardHolderInputField]) inherit from this class, override [type] and may add
 * field-specific functions on top.
 *
 * A widget only renders its internal React view once it has been bound to a
 * [io.hyperswitch.paymentmethods.CardForm] via `cardForm.bind(...)`, the same way a
 * `PaymentElement` only renders once bound to `Elements`.
 */
abstract class BaseRNViewInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Widget type sent to the RN root component — overridden by each concrete field
     * (e.g. `"cardNumberInput"`).
     */
    abstract val type: String

    private var configurationBundle: Bundle = Bundle()
    private var session: PaymentMethodSession? = null
    private var reactSurface: ReactSurface? = null
    private var reactView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        if (id == NO_ID) {
            id = View.generateViewId()
        }
    }

    /** Sets the field configuration via the typed [FieldConfiguration]. */
    fun setConfiguration(configuration: FieldConfiguration) {
        configurationBundle = configuration.toBundle()
    }

    /** Sets the field configuration via a raw prop map (RN bridge-style). */
    fun setConfiguration(configurationMap: Map<String, Any?>) {
        configurationBundle = BundleUtils.toBundle(configurationMap)
    }

    /** Adds/updates a single configuration prop (used by field-specific helpers). */
    protected fun setConfigurationProp(key: String, value: Any?) {
        when (value) {
            null -> configurationBundle.remove(key)
            is String -> configurationBundle.putString(key, value)
            is Boolean -> configurationBundle.putBoolean(key, value)
            is Int -> configurationBundle.putInt(key, value)
            is Long -> configurationBundle.putLong(key, value)
            is Double -> configurationBundle.putDouble(key, value)
            is Float -> configurationBundle.putFloat(key, value)
            else -> configurationBundle.putString(key, value.toString())
        }
    }

    /** Placeholder text prop shared by all input fields. */
    fun setPlaceholder(placeholder: String) = setConfigurationProp("placeholder", placeholder)

    /**
     * Launch options handed to the internal React view:
     * ```
     * launchOptions = {
     *     type          = "<field type>",
     *     configuration = {...},
     *     session       = { sdk_auth = "...", vault_type = "...", vault_data = "..." }
     * }
     * ```
     */
    internal val launchOptions: Bundle
        get() = session?.buildLaunchOptions(type, configurationBundle) ?: Bundle().apply {
            putBundle("props", Bundle().apply {
                putString("type", type)
                putBundle("configuration", configurationBundle)
            })
        }

    internal fun attachToSession(session: PaymentMethodSession) {
        this.session = session
    }

    /**
     * Creates the React view inside this widget (like `PaymentElement`'s internal
     * `PaymentWidgetView`) and starts rendering the field on the owning session's
     * dedicated React host. No-op until the widget is bound to a card form.
     */
    fun startInternalView() {
        val create = Runnable {
            if (reactSurface == null) {
                val host = session?.reactHost ?: run {
                    Log.w(TAG, "startInternalView() ignored — widget is not bound to a CardForm")
                    return@Runnable
                }
                runCatching {
                    val surface = host.createSurface(context, COMPONENT_NAME, launchOptions)
                    surface.start()
                    reactSurface = surface
                    surface.view?.let { view ->
                        reactView = view
                        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to start internal React view for $type: ${it.message}")
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) create.run() else mainHandler.post(create)
    }

    /** Stops the internal React view and removes it from this widget. */
    fun stopInternalView() {
        val stop = Runnable {
            reactSurface?.let { surface -> runCatching { surface.stop() } }
            reactSurface = null
            reactView?.let { removeView(it) }
            reactView = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run() else mainHandler.post(stop)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopInternalView()
    }

    private companion object {
        private const val TAG = "BaseRNViewInput"
        private const val COMPONENT_NAME = "hyperSwitch"
    }
}
