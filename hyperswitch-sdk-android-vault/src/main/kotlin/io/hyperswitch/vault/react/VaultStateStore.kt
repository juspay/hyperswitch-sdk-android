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

    fun clear() {
        states.clear()
        listeners.clear()
    }
}
