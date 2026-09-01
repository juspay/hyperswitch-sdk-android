package io.hyperswitch.vault.widget

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.interfaces.fabric.ReactSurface
import io.hyperswitch.vault.R
import io.hyperswitch.vault.core.FieldState
import io.hyperswitch.vault.core.FieldType
import io.hyperswitch.vault.core.OnFieldStateChangeListener
import io.hyperswitch.vault.core.VaultAppearance
import io.hyperswitch.vault.core.VaultFieldOptions
import io.hyperswitch.vault.react.VaultReactNativeController
import io.hyperswitch.vault.react.VaultStateStore

/**
 * BaseVaultFieldView
 *
 * Native-host container for one secure vault field. The Hyperswitch Vault SDK
 * renders the input itself as a React Native surface of the shared runtime —
 * the view you place in the merchant layout is only a host.
 *
 * ── Initial-prop shape ──────────────────────────────────────────────────────
 * Top level of `config` holds library-owned keys only:
 *
 *     type, fieldName, isRequired, sdkAuthorization, environment
 *
 * No raw card data — no `value`, no `readOnly` — ever enters the Bundle. The
 * sensitive value lives only inside the RN surface.
 *
 * Every merchant-set customization lives under `configuration`:
 *
 *     configuration: {
 *         appearance: { ...theme tokens... },
 *         options:    { ...field options... },
 *     }
 */
open class BaseVaultFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Value of the `type` initial prop of the `hs-vault` app. */
    protected open val fieldTypeName: String = "infoInput"

    protected open val defaultFieldType: FieldType = FieldType.INFO

    var fieldName: String = ""
        set(value) {
            field = value
        }
    var isRequired: Boolean = true
        private set

    /** Merchant theme tokens, gathered from XML attrs and overridable in code. */
    private var xmlAppearance: VaultAppearance? = null
    private var codeAppearance: VaultAppearance? = null

    /** Merchant field options, gathered from XML attrs and overridable in code. */
    private var xmlOptions: VaultFieldOptions? = null
    private var codeOptions: VaultFieldOptions? = null

    /* Owner-collect identity, forwarded to the field surface's config props.
     * Set by HyperswitchCollect.bindView. */
    internal var sdkAuthorization: String? = null
    internal var jsEnvironment: String? = null

    private var rootTag: Int = -1
    private var surface: ReactSurface? = null
    private var mounted = false

    private var currentState: FieldState? = null
    internal var stateChangeListener: OnFieldStateChangeListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        attrs?.let(::parseAttrs)
    }

    fun setRequired(required: Boolean) {
        isRequired = required
    }

    /** Must be set before the view is attached to a window. Merged with XML. */
    fun setAppearance(appearance: VaultAppearance) {
        this.codeAppearance = appearance
    }

    /** Must be set before the view is attached to a window. Merged with XML. */
    fun setOptions(options: VaultFieldOptions) {
        this.codeOptions = options
    }

    fun getState(): FieldState? =
        currentState ?: rootTag.takeIf { it > 0 }?.let(VaultStateStore::get)

    fun getFieldType(): FieldType = defaultFieldType
    fun getRootTag(): Int = rootTag

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) mountSurface()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (rootTag > 0) VaultStateStore.remove(rootTag)
        rootTag = -1
        surface?.view?.let { removeView(it) }
        surface = null
        mounted = false
    }

    private fun parseAttrs(attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.HyperswitchVaultField)
        try {
            fieldName = a.getString(R.styleable.HyperswitchVaultField_fieldName) ?: fieldName
            isRequired = a.getBoolean(R.styleable.HyperswitchVaultField_isRequired, isRequired)

            val placeholder = a.getString(R.styleable.HyperswitchVaultField_placeholder)

            // ── field options ──
            val label = a.getString(R.styleable.HyperswitchVaultField_vaultLabel)
            val labelBehavior = when (a.getInt(R.styleable.HyperswitchVaultField_vaultLabelBehavior, -1)) {
                1 -> VaultFieldOptions.LabelBehavior.STATIC
                2 -> VaultFieldOptions.LabelBehavior.FLOATING
                0 -> VaultFieldOptions.LabelBehavior.NONE
                else -> null
            }
            val errorDisplay = when (a.getInt(R.styleable.HyperswitchVaultField_vaultErrorDisplay, -1)) {
                1 -> VaultFieldOptions.ErrorDisplay.INLINE
                0 -> VaultFieldOptions.ErrorDisplay.NONE
                else -> null
            }
            val brandIconMode = when (a.getInt(R.styleable.HyperswitchVaultField_vaultBrandIconMode, -1)) {
                0 -> VaultAppearance.BrandIconMode.AUTO
                1 -> VaultAppearance.BrandIconMode.STATIC
                2 -> VaultAppearance.BrandIconMode.HIDDEN
                else -> null
            }
            val cvcIcon = when (a.getInt(R.styleable.HyperswitchVaultField_vaultCvcIcon, -1)) {
                1 -> VaultFieldOptions.CvcIcon.DEFAULT
                0 -> VaultFieldOptions.CvcIcon.NONE
                else -> null
            }
            val accessibilityLabel = a.getString(R.styleable.HyperswitchVaultField_vaultAccessibilityLabel)
            val accessibilityHint = a.getString(R.styleable.HyperswitchVaultField_vaultAccessibilityHint)
            val testTag = a.getString(R.styleable.HyperswitchVaultField_vaultTestTag)

            if (listOf(placeholder, label, labelBehavior, errorDisplay, brandIconMode,
                    cvcIcon, accessibilityLabel, accessibilityHint, testTag).any { it != null }
            ) {
                xmlOptions = VaultFieldOptions(
                    placeholder = placeholder,
                    label = label,
                    labelBehavior = labelBehavior,
                    errorDisplay = errorDisplay,
                    accessibilityLabel = accessibilityLabel,
                    accessibilityHint = accessibilityHint,
                    testID = testTag,
                    brandIconMode = brandIconMode,
                    cvcIcon = cvcIcon,
                )
            }

            // ── appearance tokens ──
            val primary = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultPrimaryColor)
            val text = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultTextColor)
            val error = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultErrorColor)
            val placeholderColor = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultPlaceholderColor)
            val background = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultBackgroundColor)
            val border = a.getColorOrNull(R.styleable.HyperswitchVaultField_vaultBorderColor)
            val radius = a.getDimensionOrNull(R.styleable.HyperswitchVaultField_vaultBorderRadius)
            val borderWidth = a.getDimensionOrNull(R.styleable.HyperswitchVaultField_vaultBorderWidth)
            val inputHeight = a.getDimensionOrNull(R.styleable.HyperswitchVaultField_vaultInputHeight)
            val fontScale = if (a.hasValue(R.styleable.HyperswitchVaultField_vaultFontScale)) {
                a.getFloat(R.styleable.HyperswitchVaultField_vaultFontScale, 1f)
            } else null
            val fontFamily = a.getString(R.styleable.HyperswitchVaultField_vaultFontFamily)

            if (listOf(primary, text, error, placeholderColor, background, border,
                    radius, borderWidth, inputHeight, fontScale, fontFamily).any { it != null }
            ) {
                xmlAppearance = VaultAppearance(
                    primaryColor = primary,
                    textColor = text,
                    errorColor = error,
                    placeholderColor = placeholderColor,
                    backgroundColor = background,
                    borderColor = border,
                    borderRadius = radius?.let { pxToDp(it) },
                    borderWidth = borderWidth?.let { pxToDp(it) },
                    inputHeight = inputHeight?.let { pxToDp(it) },
                    fontScale = fontScale,
                    fontFamily = fontFamily,
                    brandIconMode = brandIconMode,
                )
            }
        } finally {
            a.recycle()
        }
    }

    protected open fun buildInitialProps(): Bundle = Bundle().apply {
        putString("type", fieldTypeName)
        putBundle("config", Bundle().apply {
            // ── internal (library-owned) ──
            if (fieldName.isNotBlank()) putString("fieldName", fieldName)
            putBoolean("isRequired", isRequired)
            sdkAuthorization?.let { putString("sdkAuthorization", it) }
            jsEnvironment?.let { putString("environment", it) }

            // ── merchant-owned ──
            val appearance = VaultAppearance.merge(xmlAppearance, codeAppearance)
            val options = VaultFieldOptions.merge(xmlOptions, codeOptions)
            if (appearance != null || options != null) {
                putBundle("configuration", Bundle().apply {
                    appearance?.let { putBundle("appearance", it.toBundle()) }
                    options?.let { putBundle("options", it.toBundle()) }
                })
            }
        })
    }

    private fun mountSurface() {
        if (mounted) return
        mounted = true
        val application = context.applicationContext as Application
        VaultReactNativeController.initialize(application)
        VaultReactNativeController.withReactContext(application) {
            mainHandler.post { attachSurface() }
        }
    }

    private fun attachSurface() {
        if (surface != null) return
        try {
            // RN surfaces only commit while the shared host is RESUMED, so the
            // field forwards its own activity to the host (idempotent). This
            // keeps consumers free of any RN lifecycle boilerplate.
            (context as? Activity)?.let {
                VaultReactNativeController.getReactHost().onHostResume(it)
            }
            val newSurface = VaultReactNativeController.getReactHost()
                .createSurface(
                    context,
                    VaultReactNativeController.VAULT_MODULE_NAME,
                    buildInitialProps(),
                )
            surface = newSurface
            newSurface.start()
            val view = newSurface.view
                ?: throw IllegalStateException("Vault field surface has no view")
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            resolveRootTag(newSurface)
        } catch (e: Throwable) {
            mounted = false
            android.util.Log.e("VaultFieldView", "Failed to mount vault field surface", e)
        }
    }

    /**
     * Fabric assigns the surface's rootTag when the surface actually starts
     * (see FabricUIManager.startSurface / ReactRootViewTagGenerator); before
     * that [ReactSurface.surfaceID] is NO_SURFACE_ID (0). Poll briefly so the
     * state subscription is keyed by the real tag — the same one JS receives
     * as `props.rootTag`.
     */
    private fun resolveRootTag(surface: ReactSurface, attempts: Int = 40) {
        if (surface.surfaceID > 0) {
            onRootTag(surface.surfaceID)
            return
        }
        if (attempts <= 0) return
        mainHandler.postDelayed({ resolveRootTag(surface, attempts - 1) }, 50)
    }

    private fun onRootTag(tag: Int) {
        rootTag = tag
        VaultStateStore.subscribe(tag) { state ->
            currentState = state
            stateChangeListener?.onStateChange(state)
        }
        /*
         * The JS vault package pushes redacted states keyed by FieldType (one
         * shared runtime may serve several field surfaces). fieldName from
         * the view's own attrs is merged in — the JS side never sees it.
         */
        VaultStateStore.subscribeByType(defaultFieldType.rawValue) { state ->
            val merged =
                if (state.fieldName.isNullOrBlank() && fieldName.isNotBlank()) {
                    state.copy(fieldName = fieldName)
                } else {
                    state
                }
            currentState = merged
            stateChangeListener?.onStateChange(merged)
        }
    }

    private fun pxToDp(px: Float): Float =
        px / resources.displayMetrics.density

    private fun android.content.res.TypedArray.getColorOrNull(
        styleable: Int,
    ): Int? = if (hasValue(styleable)) getColor(styleable, 0) else null

    private fun android.content.res.TypedArray.getDimensionOrNull(
        styleable: Int,
    ): Float? = if (hasValue(styleable)) getDimension(styleable, 0f) else null
}
