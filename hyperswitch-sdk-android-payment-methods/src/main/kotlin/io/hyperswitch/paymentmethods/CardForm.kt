package io.hyperswitch.paymentmethods

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.interfaces.fabric.ReactSurface
import io.hyperswitch.paymentmethods.widget.BaseRNViewInput
import io.hyperswitch.paymentsheet.PaymentSheet
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
    private var appearance: PaymentSheet.Appearance? = null,
    private var variables: AppearanceVariables? = null,
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
            val appearanceBundle = Bundle().apply {
                appearance?.let { putAll(it.bundle) }
                variables?.toMap()?.takeIf { it.isNotEmpty() }
                    ?.let { putBundle("variables", BundleUtils.toBundle(it)) }
            }
            val configuration = if (!appearanceBundle.isEmpty) {
                Bundle().apply { putBundle("appearance", appearanceBundle) }
            } else null
            val surface = session.reactHost.createSurface(
                session.activity,
                COMPONENT_NAME,
                session.buildLaunchOptions(TYPE, configuration),
            )
            surface.start()
            formSurface = surface
        }.onFailure {
            Log.e(TAG, "Failed to start card form surface: ${it.message}")
            formSurface = null
        }
    }

    /**
     * Asks the JS side of this session's host to tokenize this card form.
     *
     * The event is addressed at this card form's (empty) surface — the JS-side
     * card-form controller picks it up and performs tokenization for all bound
     * fields via `cardForm.tokenize()`. The result travels back through
     * `PaymentMethodModule.returnTokenResult` and is delivered to [onComplete];
     * one tokenise may be in flight per form at a time.
     */
    fun tokenise(onComplete: (TokeniseResult) -> Unit = {}) {
        val rootTag = formSurface?.surfaceID ?: -1
        session.registerTokeniseCallback(rootTag) { raw -> onComplete(TokeniseResult.from(raw)) }
        session.emitTokenise(rootTag)
    }

    /**
     * Updates this card form's vault/session-wide theming — the same [PaymentSheet.Appearance]
     * and [AppearanceVariables] [PaymentMethodSession.createCardForm] takes, settable after the
     * fact instead of only at creation. This is the vault-level counterpart to
     * [io.hyperswitch.paymentmethods.widget.BaseRNViewInput.setOptions], which carries a single
     * field's own [FieldStyles]/[FieldOptions] instead.
     *
     * The JS side has no live-update path for these props (see [HeadlessSurface][
     * io.hyperswitch.paymentsession.HeadlessSurface] for the same convention elsewhere in this
     * SDK), so this stops and restarts the empty form surface with the new values. Bound input
     * widgets are unaffected — they're independent surfaces that keep running.
     */
    fun setOptions(appearance: PaymentSheet.Appearance? = null, variables: AppearanceVariables? = null) {
        this.appearance = appearance
        this.variables = variables
        mainHandler.post {
            formSurface?.let { runCatching { it.stop() } }
            formSurface = null
            startFormSurface()
        }
    }

    /**
     * Binds a single input widget to this card form — starts its internal React view.
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

    /**
     * Stops every bound input's React view and the card form's empty surface.
     *
     * Must run synchronously when already on the main thread: [PaymentMethodSession.release]
     * is expected to follow this immediately and destroys the underlying [ReactHost][
     * com.facebook.react.ReactHost], whose Fabric `Scheduler` asserts that every surface was
     * already stopped — an unconditional `mainHandler.post` here would defer this surface's
     * stop past that destroy, aborting the process (`Scheduler was destroyed with outstanding
     * Surfaces`).
     */
    fun release() {
        boundInputs.forEach { it.stopInternalView() }
        boundInputs.clear()
        val stop = Runnable {
            formSurface?.let { runCatching { it.stop() } }
            formSurface = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run() else mainHandler.post(stop)
    }

    internal companion object {
        private const val TAG = "CardForm"
        private const val COMPONENT_NAME = "HyperswitchPaymentMethods"

        /** surface `type` for the empty card-form controller view. */
        private const val TYPE = "cardForm"
    }
}
