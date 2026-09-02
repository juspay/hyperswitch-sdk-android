package io.hyperswitch.vault.react

import android.util.Log
import io.hyperswitch.vault.core.VaultTokeniseRequest
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * VaultEventEmitter
 *
 * Holder for the live [HyperVaultModule] instance so non-React callers
 * (HyperswitchVault via VaultReactNativeController) can broadcast the typed
 * codegen event `onVaultTokenise` without going through ReactContext
 * acquisition. Twin of the main SDK's HyperEventEmitter (triggerWidgetAction
 * channel).
 *
 * The broadcast is fire-and-forget: if JS never answered (no CVC surface
 * mounted), [TokeniseDispatcher]'s 30s net resolves the merchant completion.
 */
object VaultEventEmitter {

    private val moduleRef = AtomicReference<WeakReference<HyperVaultModule>?>(null)

    fun attach(module: HyperVaultModule) {
        moduleRef.set(WeakReference(module))
    }

    fun detach() {
        moduleRef.set(null)
    }

    /**
     * Broadcasts a typed tokenise request to the JS vault surfaces; the CVC
     * surface claims it and answers via HyperVaultModule.returnTokenizedValue.
     */
    fun emitVaultTokenise(request: VaultTokeniseRequest) {
        val module = moduleRef.get()?.get() ?: return
        try {
            module.emitVaultTokenise(request)
        } catch (e: Exception) {
            Log.w("VaultEventEmitter", "emitVaultTokenise dropped: ${e.message}")
        }
    }
}
