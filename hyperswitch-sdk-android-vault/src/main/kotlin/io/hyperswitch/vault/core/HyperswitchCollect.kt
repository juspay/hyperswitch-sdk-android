package io.hyperswitch.vault.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.hyperswitch.vault.BuildConfig
import io.hyperswitch.vault.react.TokeniseDispatcher
import io.hyperswitch.vault.react.VaultReactNativeController
import io.hyperswitch.vault.react.VaultStateStore
import io.hyperswitch.vault.widget.BaseVaultFieldView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * HyperswitchCollect
 *
 * (`io.hyperswitch:hyperswitch-vault-sdk-android`).
 *
 * ```kotlin
 * val collect = HyperswitchCollect(this, "vault_42", Environment.SANDBOX)
 * collect.bindView(cardNumberField)
 * collect.bindView(expDateField)
 * collect.bindView(cvcField)
 * collect.addOnSubmitListeners { response -> ... }
 * collect.asyncSubmit(VaultRequest.builder().setPath("/tokenize").build())
 * ```
 *
 * The sensitive inputs are rendered by React Native surfaces inside the bound
 * views; this class orchestrates the VGS-shaped native API: binding, state
 * tracking, submit, aliases replacement.
 */
class HyperswitchCollect {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val views = LinkedHashSet<BaseVaultFieldView>()
    private var fieldStateChangeListener: OnFieldStateChangeListener? = null

    /** Hyperswitch Vault SDK authorization token (identifies the vault). */
    val sdkAuthorization: String
    val environment: Environment
    private val customBaseUrl: String?

    @JvmOverloads
    constructor(context: Context, sdkAuthorization: String, environment: Environment) {
        this.sdkAuthorization = sdkAuthorization
        this.environment = environment
        this.customBaseUrl = null
        VaultReactNativeController.initialize(context.applicationContext as android.app.Application)
    }

    constructor(context: Context, sdkAuthorization: String, environment: Environment, url: String) {
        this.sdkAuthorization = sdkAuthorization
        this.environment = environment
        this.customBaseUrl = url
        VaultReactNativeController.initialize(context.applicationContext as android.app.Application)
    }

    /** Binds a secure vault field to this collect instance. */
    fun bindView(view: BaseVaultFieldView?) {
        if (view == null) return
        views.add(view)
        view.stateChangeListener = OnFieldStateChangeListener { state ->
            fieldStateChangeListener?.onStateChange(state)
        }
    }

    fun unbindView(view: BaseVaultFieldView) {
        views.remove(view)
        view.stateChangeListener = null
    }

    fun setOnFieldStateChangeListener(listener: OnFieldStateChangeListener?) {
        fieldStateChangeListener = listener
    }

    /**
     * Tokenises the values of every bound field and reports the outcome on
     * the main thread.
     *
     * The broadcast is claimed by exactly one mounted JS vault surface, which
     * runs the payment-method-session confirm from the shared JS registry and
     * answers with the vaultSubmitResult JSON; the JS-side timeout (
     * TokeniseDispatcher) guarantees [completion] fires even when no surface
     * is mounted.
     */
    fun tokenise(completion: (VaultTokeniseResult) -> Unit) {
        val requestId = TokeniseDispatcher.newRequestId()
        TokeniseDispatcher.register(requestId) { json ->
            completion(VaultTokeniseResult.fromJson(json))
        }
        VaultReactNativeController.emitTokenise(
            requestId,
            sdkAuthorization,
            environment.jsEnvName,
        )
    }

    fun getFieldStates(): Collection<FieldState> =
        views.mapNotNull { it.getState() }

    fun getFieldState(view: BaseVaultFieldView): FieldState? = view.getState()

    fun onDestroyFields() {
        views.forEach { it.stateChangeListener = null }
        views.clear()
        executor.shutdown()
    }

    companion object {
        val VERSION: String = BuildConfig.VERSION_NAME
    }
}
