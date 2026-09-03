package io.hyperswitch.paymentmethods

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.interfaces.fabric.ReactSurface
import io.hyperswitch.paymentmethods.widget.BaseRNViewInput
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A card-form instance created via [PaymentMethodSession.createCardForm].
 *
 * On creation it starts an **empty RN view ("headless")** on the owning session's
 * dedicated React host — the JS-side form controller — and groups the input widgets
 * bound to it via [bind].
 *
 * Mirrors the `PaymentElement`/`Elements.bind()` flow of the main payment SDK:
 * [bind] starts an internal React view inside each bound [BaseRNViewInput].
 */
class CardForm internal constructor(
    internal val session: PaymentMethodSession,
) {

    private val boundInputs = CopyOnWriteArrayList<BaseRNViewInput>()

    /** The empty RN surface backing this card form on the session's own host. */
    @Volatile
    private var formSurface: ReactSurface? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post { startFormSurface() }
    }

    /**
     * Creates the empty RN view backing this card form.
     * The surface is started but never attached to any parent — it only hosts the
     * JS-side card-form controller for the bound input widgets.
     */
    private fun startFormSurface() {
        runCatching {
            val surface = session.reactHost.createSurface(
                session.activity,
                COMPONENT_NAME,
                session.buildLaunchOptions(TYPE, null),
            )
            surface.start()
            formSurface = surface
        }.onFailure {
            Log.e(TAG, "Failed to start card form surface: ${it.message}")
            formSurface = null
        }
    }

    /**
     * Binds a single input widget to this card form — starts its internal React view
     * (see [BaseRNViewInput.startInternalView]).
     */
    fun bind(input: BaseRNViewInput): BaseRNViewInput = bind(listOf(input)).first()

    /**
     * Binds the given input widgets to this card form.
     * Each widget gets its own React view inside itself, rendered on the
     * owning session's dedicated React host.
     */
    fun bind(inputs: List<BaseRNViewInput>): List<BaseRNViewInput> {
        mainHandler.post {
            inputs.forEach { input ->
                if (!boundInputs.contains(input)) {
                    input.attachToSession(session)
                    input.startInternalView()
                    boundInputs.add(input)
                }
            }
        }
        return inputs
    }

    /** Unbinds a previously bound input widget and stops its internal React view. */
    fun unbind(input: BaseRNViewInput) {
        if (boundInputs.remove(input)) {
            input.stopInternalView()
        }
    }

    /** All inputs currently bound to this card form. */
    fun getBoundInputs(): List<BaseRNViewInput> = boundInputs.toList()

    /** Stops every bound input's React view and the card form's empty surface. */
    fun release() {
        boundInputs.forEach { it.stopInternalView() }
        boundInputs.clear()
        mainHandler.post {
            formSurface?.let { runCatching { it.stop() } }
            formSurface = null
        }
    }

    internal companion object {
        private const val TAG = "CardForm"
        private const val COMPONENT_NAME = "hyperSwitch"

        /** surface `type` for the empty card-form controller view. */
        private const val TYPE = "cardForm"
    }
}
