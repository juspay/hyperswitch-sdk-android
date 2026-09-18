package io.hyperswitch.paymentmethods.widget

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.interfaces.fabric.ReactSurface
import io.hyperswitch.paymentmethods.BrandIconMode
import io.hyperswitch.paymentmethods.BundleUtils
import io.hyperswitch.paymentmethods.CvcIconDisplay
import io.hyperswitch.paymentmethods.ErrorDisplay
import io.hyperswitch.paymentmethods.FieldConfiguration
import io.hyperswitch.paymentmethods.FieldOptions
import io.hyperswitch.paymentmethods.FieldStyles
import io.hyperswitch.paymentmethods.LabelBehavior
import io.hyperswitch.paymentmethods.PaymentMethodSession
import io.hyperswitch.paymentmethods.R
import io.hyperswitch.paymentmethods.TextStyleProps
import io.hyperswitch.paymentmethods.ViewStyleProps

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
        attrs?.let { applyStyleAttributes(context, it, defStyleAttr) }
    }

    /**
     * Reads the `field*` XML attributes (see `attrs.xml`) and forwards them as this widget's
     * `styles` (all 7 [FieldStyles] slots) and `options` ([FieldOptions]) configuration — the
     * same shapes the JS-side `FieldStyles`/`FieldOptions` contracts expect. `android:
     * layout_height` also sizes the widget itself (a plain [FrameLayout]) natively, but that
     * alone doesn't tell the JS-rendered field its intended height, so an explicit
     * `layout_height` dimension is forwarded as `styles.container.height` too.
     */
    private fun applyStyleAttributes(context: Context, attrs: AttributeSet, defStyleAttr: Int) {
        val density = context.resources.displayMetrics.density
        val ta = context.obtainStyledAttributes(attrs, R.styleable.BaseRNViewInput, defStyleAttr, 0)
        try {
            fun viewStyle(
                backgroundColorAttr: Int,
                borderRadiusAttr: Int,
                borderWidthAttr: Int,
                borderColorAttr: Int,
                paddingAttr: Int,
                extraHeight: Float? = null,
            ): ViewStyleProps = ViewStyleProps(
                backgroundColor = ta.getColorOrNull(backgroundColorAttr),
                borderRadius = ta.getDimensionOrNull(borderRadiusAttr, density),
                borderWidth = ta.getDimensionOrNull(borderWidthAttr, density),
                borderColor = ta.getColorOrNull(borderColorAttr),
                padding = ta.getDimensionOrNull(paddingAttr, density),
                height = extraHeight,
            )

            fun textStyle(colorAttr: Int, fontSizeAttr: Int): TextStyleProps = TextStyleProps(
                color = ta.getColorOrNull(colorAttr),
                fontSize = ta.getDimensionOrNull(fontSizeAttr, density),
            )

            val layoutHeightAttrs = context.obtainStyledAttributes(attrs, LAYOUT_HEIGHT_ATTRS)
            val rawLayoutHeight =
                layoutHeightAttrs.getLayoutDimension(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutHeightAttrs.recycle()
            val containerHeight = if (rawLayoutHeight >= 0) rawLayoutHeight / density else null
            // The JS-rendered box's height: fieldInnerHeight wins when given, otherwise fall
            // back to android:layout_height (the native widget's own size) — see attrs.xml.
            val explicitHeight =
                ta.getDimensionOrNull(R.styleable.BaseRNViewInput_fieldInnerHeight, density)
                    ?: containerHeight

            val styles = FieldStyles(
                root = viewStyle(
                    R.styleable.BaseRNViewInput_fieldRootBackgroundColor,
                    R.styleable.BaseRNViewInput_fieldRootBorderRadius,
                    R.styleable.BaseRNViewInput_fieldRootBorderWidth,
                    R.styleable.BaseRNViewInput_fieldRootBorderColor,
                    R.styleable.BaseRNViewInput_fieldRootPadding,
                ),
                container = viewStyle(
                    R.styleable.BaseRNViewInput_fieldContainerBackgroundColor,
                    R.styleable.BaseRNViewInput_fieldContainerBorderRadius,
                    R.styleable.BaseRNViewInput_fieldContainerBorderWidth,
                    R.styleable.BaseRNViewInput_fieldContainerBorderColor,
                    R.styleable.BaseRNViewInput_fieldContainerPadding,
                    extraHeight = explicitHeight,
                ),
                input = textStyle(
                    R.styleable.BaseRNViewInput_fieldInputTextColor,
                    R.styleable.BaseRNViewInput_fieldInputFontSize,
                ),
                placeholder = textStyle(
                    R.styleable.BaseRNViewInput_fieldPlaceholderTextColor,
                    R.styleable.BaseRNViewInput_fieldPlaceholderFontSize,
                ),
                label = textStyle(
                    R.styleable.BaseRNViewInput_fieldLabelTextColor,
                    R.styleable.BaseRNViewInput_fieldLabelFontSize,
                ),
                error = textStyle(
                    R.styleable.BaseRNViewInput_fieldErrorTextColor,
                    R.styleable.BaseRNViewInput_fieldErrorFontSize,
                ),
                accessory = viewStyle(
                    R.styleable.BaseRNViewInput_fieldAccessoryBackgroundColor,
                    R.styleable.BaseRNViewInput_fieldAccessoryBorderRadius,
                    R.styleable.BaseRNViewInput_fieldAccessoryBorderWidth,
                    R.styleable.BaseRNViewInput_fieldAccessoryBorderColor,
                    R.styleable.BaseRNViewInput_fieldAccessoryPadding,
                ),
            )
            if (!styles.isEmpty()) setConfigurationBundleProp("styles", styles.toMap())

            val options = FieldOptions(
                label = ta.getString(R.styleable.BaseRNViewInput_fieldLabel),
                labelBehavior = when (ta.getInt(R.styleable.BaseRNViewInput_fieldLabelBehavior, -1)) {
                    0 -> LabelBehavior.ABOVE
                    1 -> LabelBehavior.FLOATING
                    2 -> LabelBehavior.NEVER
                    else -> null
                },
                errorDisplay = when (ta.getInt(R.styleable.BaseRNViewInput_fieldErrorDisplay, -1)) {
                    0 -> ErrorDisplay.NONE
                    1 -> ErrorDisplay.COLOR_ONLY
                    2 -> ErrorDisplay.INLINE
                    else -> null
                },
                unstyled = if (ta.hasValue(R.styleable.BaseRNViewInput_fieldUnstyled)) {
                    ta.getBoolean(R.styleable.BaseRNViewInput_fieldUnstyled, false)
                } else null,
                accessibilityLabel = ta.getString(R.styleable.BaseRNViewInput_fieldAccessibilityLabel),
                accessibilityHint = ta.getString(R.styleable.BaseRNViewInput_fieldAccessibilityHint),
                cardBrandIcon = when (ta.getInt(R.styleable.BaseRNViewInput_fieldCardBrandIcon, -1)) {
                    0 -> BrandIconMode.STANDARD
                    1 -> BrandIconMode.ANIMATED
                    2 -> BrandIconMode.HIDDEN
                    3 -> BrandIconMode.HIDE_GENERIC
                    else -> null
                },
                cvcIcon = when (ta.getInt(R.styleable.BaseRNViewInput_fieldCvcIcon, -1)) {
                    0 -> CvcIconDisplay.HIDDEN
                    1 -> CvcIconDisplay.DEFAULT
                    else -> null
                },
            )
            if (!options.isEmpty()) setConfigurationBundleProp("options", options.toMap())

            ta.getString(R.styleable.BaseRNViewInput_fieldPlaceholder)?.let { setPlaceholder(it) }
        } finally {
            ta.recycle()
        }
    }

    private fun android.content.res.TypedArray.getColorOrNull(index: Int): Int? =
        if (hasValue(index)) getColor(index, 0) else null

    private fun android.content.res.TypedArray.getDimensionOrNull(index: Int, density: Float): Float? =
        if (hasValue(index)) getDimension(index, 0f) / density else null

    /**
     * Sets the field configuration via the typed [FieldConfiguration] — the code-first
     * alternative to the `app:field*` XML attributes (see `attrs.xml`). Native forwards
     * whatever is given here to the JS side as-is; JS (`react-native-hyperswitch-payment-
     * methods` / the vault SDK) owns interpreting it (e.g. how a `styles.container.height`
     * interacts with `options.labelBehavior`) — native doesn't second-guess it.
     */
    fun setConfiguration(configuration: FieldConfiguration) {
        configurationBundle = configuration.toBundle()
    }

    /** Sets the field configuration via a raw prop map (RN bridge-style). */
    fun setConfiguration(configurationMap: Map<String, Any?>) {
        configurationBundle = BundleUtils.toBundle(configurationMap)
    }

    /**
     * Ergonomic wrapper around [setConfiguration] for this field's own styling/options — the
     * per-field counterpart to [io.hyperswitch.paymentmethods.CardForm.setOptions] (which
     * carries the vault/session-wide `Appearance`/`AppearanceVariables` instead). Use this or
     * the `app:field*` XML attributes — not both for the same widget.
     * ```
     * cardHolderInput.setOptions(
     *     styles = FieldStyles(container = ViewStyleProps(borderWidth = 3f, height = 56f), ...),
     *     options = FieldOptions(label = "Cardholder name", labelBehavior = LabelBehavior.ABOVE),
     *     placeholder = "Cardholder name",
     * )
     * ```
     */
    fun setOptions(
        styles: FieldStyles? = null,
        options: FieldOptions? = null,
        placeholder: String? = null,
    ) = setConfiguration(
        FieldConfiguration(typedStyles = styles, options = options, placeholder = placeholder)
    )

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

    /** Adds/updates a nested configuration prop, e.g. `styles = { container, input }`. */
    private fun setConfigurationBundleProp(key: String, value: Map<String, Any?>) {
        configurationBundle.putBundle(key, BundleUtils.toBundle(value))
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
        private const val COMPONENT_NAME = "HyperswitchPaymentMethods"
        private val LAYOUT_HEIGHT_ATTRS = intArrayOf(android.R.attr.layout_height)
    }
}
