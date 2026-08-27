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
import io.hyperswitch.vault.react.VaultReactNativeController
import io.hyperswitch.vault.react.VaultStateStore

/**
 * BaseVaultFieldView
 *
 * Native-host container for one secure vault field. the Hyperswitch Vault SDK renders the
 * input itself as a React Native surface of the shared runtime — the view you
 * place in the merchant layout is only a host.
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
    var placeholder: String? = null
    private var appearance: VaultAppearance? = null

    /** Vault alias shown after a successful tokenization (read-only). */
    private var alias: String? = null

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

    /** Must be set before the view is attached to a window. */
    fun setAppearance(appearance: VaultAppearance) {
        this.appearance = appearance
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
        fieldName = a.getString(R.styleable.HyperswitchVaultField_fieldName) ?: fieldName
        isRequired = a.getBoolean(R.styleable.HyperswitchVaultField_isRequired, isRequired)
        placeholder = a.getString(R.styleable.HyperswitchVaultField_placeholder) ?: placeholder
        a.recycle()
    }

    protected open fun buildInitialProps(): Bundle = Bundle().apply {
        putString("type", fieldTypeName)
        putBundle("config", Bundle().apply {
            if (fieldName.isNotBlank()) putString("fieldName", fieldName)
            placeholder?.let { putString("placeholder", it) }
            putBoolean("isRequired", isRequired)
            alias?.let {
                putString("value", it)
                putBoolean("readOnly", true)
            }
            appearance?.toBundle()?.let { putBundle("appearance", it) }
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
    }

    /**
     * Hyperswitch-style `setText(token)`: replaces the sensitive content with the vault
     * alias by re-mounting this field's surface read-only.
     */
    internal fun setAlias(token: String) {
        alias = token
        surface?.view?.let { removeView(it) }
        surface = null
        rootTag = -1
        attachSurface()
    }
}
