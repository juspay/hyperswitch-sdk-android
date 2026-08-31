package io.hyperswitch.vault.react

import io.hyperswitch.vault.core.FieldState
import java.util.concurrent.ConcurrentHashMap

/**
 * Latest state (keyed by surface rootTag) of every mounted vault field.
 * Fed by [HyperVaultModule.updateFieldState]; consumed by
 * [io.hyperswitch.vault.core.HyperswitchCollect] for VGS-style submit calls.
 */
internal object VaultStateStore {

    private val states = ConcurrentHashMap<Int, FieldState>()
    private val listeners = ConcurrentHashMap<Int, (FieldState) -> Unit>()

    /*
     * FieldType-keyed channel fed by HyperVaultModule.updateVaultFieldStates:
     * the aggregated, redacted states pushed by the JS vault package. Each
     * native field subscribes by its FieldType, so one shared JS runtime can
     * serve several field surfaces while each native view receives only its
     * own field's state.
     */
    private val typeStates = ConcurrentHashMap<String, FieldState>()
    private val typeListeners = ConcurrentHashMap<String, (FieldState) -> Unit>()

    fun put(rootTag: Int, state: FieldState) {
        states[rootTag] = state
        listeners[rootTag]?.invoke(state)
    }

    fun get(rootTag: Int): FieldState? = states[rootTag]

    fun remove(rootTag: Int) {
        states.remove(rootTag)
        listeners.remove(rootTag)
    }

    fun all(): Map<Int, FieldState> = HashMap(states)

    fun subscribe(rootTag: Int, onChange: (FieldState) -> Unit) {
        listeners[rootTag] = onChange
        states[rootTag]?.let(onChange)
    }

    /** [fieldType] is a FieldType.rawValue, e.g. "card_number". */
    fun putByType(fieldType: String, state: FieldState) {
        typeStates[fieldType] = state
        typeListeners[fieldType]?.invoke(state)
    }

    fun getByType(fieldType: String): FieldState? = typeStates[fieldType]

    fun subscribeByType(fieldType: String, onChange: (FieldState) -> Unit) {
        typeListeners[fieldType] = onChange
        typeStates[fieldType]?.let(onChange)
    }

    fun clear() {
        states.clear()
        listeners.clear()
        typeStates.clear()
        typeListeners.clear()
    }
}
