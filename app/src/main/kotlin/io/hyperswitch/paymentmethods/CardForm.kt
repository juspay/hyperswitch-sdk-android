package io.hyperswitch.paymentmethods

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Arguments
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.paymentmethods.react.PaymentMethodsEventTarget
import io.hyperswitch.paymentmethods.react.PaymentMethodsProtocol
import io.hyperswitch.paymentmethods.react.PaymentMethodsRuntime
import io.hyperswitch.paymentsession.HeadlessSurface
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One card form. It hands out its fields as views to place anywhere; they are one form
 * because this object made them, with nothing to link or register. The card lives until
 * [close], which the Activity's destruction calls for you. Use from the main thread.
 */
class CardForm internal constructor(
    private val activity: Activity,
    hyperswitchConfiguration: HyperswitchBaseConfiguration,
    sessionConfiguration: PaymentMethodSessionConfiguration,
    configuration: Configuration,
) {

    data class Configuration(val locale: String? = null)

    /** The fields can take input and [tokenize] can run. */
    var onReady: (() -> Unit)? = null
    var onChange: ((CardFormState) -> Unit)? = null

    /** The form cannot work: the session was refused, or its vault cannot be shown here. */
    var onError: ((CardFormError) -> Unit)? = null

    /** The latest card-free snapshot, or null before the first field has reported. */
    var state: CardFormState? = null
        private set

    val isReady: Boolean get() = phase is Phase.Ready

    internal val formId: String = UUID.randomUUID().toString()
    internal val runtime = PaymentMethodsRuntime.get(activity.application)

    private sealed interface Phase {
        data object Opening : Phase
        data object Ready : Phase
        data class Failed(val message: String) : Phase
    }

    private var phase: Phase = Phase.Opening

    /** Commands reach the bundle only once it listens, which the form's first word proves. */
    private val whenSettled = mutableListOf<() -> Unit>()
    private var pendingTokenize: Pair<String, (TokenizeResult) -> Unit>? = null

    /** Its own fields, so that [close] can end their screens too. Not a lookup of any kind. */
    private val fields = mutableListOf<WeakReference<CardFieldView>>()
    private var surface: HeadlessSurface?

    /** Removes the host-failure listener; see init. */
    private var stopWatchingHost: () -> Unit = {}

    /** What the bundle says about this form lands here. Held by the form, so it lasts as long. */
    private val events = PaymentMethodsEventTarget(::onEvent)

    init {
        runtime.follow(activity)

        val props = Bundle().apply {
            putString("type", PaymentMethodsProtocol.FORM_TYPE)
            putInt("protocolVersion", PaymentMethodsProtocol.VERSION)
            putString("formId", formId)
            putBundle("hyper", hyperswitchConfiguration.toBundle())
            putString("sdkAuthorization", sessionConfiguration.sdkAuthorization)
            configuration.locale?.let { putString("locale", it) }
        }

        /* A root that never joins a window. Stopping it is what ends the form in the bundle.
           None when the host could not start: the form then fails through onError, posted
           so a listener set right after construction still hears it. */
        surface = if (runtime.health.initFailure != null) null else HeadlessSurface.start(
            activity.applicationContext,
            runtime.reactHost,
            Bundle().apply { putBundle("props", props) },
            owner = events,
            moduleName = PaymentMethodsProtocol.FORM_COMPONENT,
        )
        stopWatchingHost = runtime.health.onFailure { error ->
            failWith(error.message ?: "The Payment Methods SDK failed to initialise.")
        }

        closeWith(activity)
    }

    // Fields

    fun cardNumberField(options: CardFieldOptions = CardFieldOptions(), context: Context = activity) =
        field(CardElementType.CARD_NUMBER, options, context)

    fun cardExpiryField(options: CardFieldOptions = CardFieldOptions(), context: Context = activity) =
        field(CardElementType.CARD_EXPIRY, options, context)

    fun cardCvcField(options: CardFieldOptions = CardFieldOptions(), context: Context = activity) =
        field(CardElementType.CARD_CVC, options, context)

    fun cardholderNameField(options: CardFieldOptions = CardFieldOptions(), context: Context = activity) =
        field(CardElementType.CARDHOLDER_NAME, options, context)

    private fun field(elementType: CardElementType, options: CardFieldOptions, context: Context): CardFieldView =
        CardFieldView(context, this, elementType, options).also { fields.add(WeakReference(it)) }

    // Tokenize

    /**
     * Sends the card to the vault and returns its token. Never throws: a card that cannot be
     * tokenized is a [TokenizeResult.Failure], and the fields show why.
     */
    @JvmSynthetic
    suspend fun tokenize(): TokenizeResult = suspendCancellableCoroutine { continuation ->
        tokenize { if (continuation.isActive) continuation.resume(it) }
    }

    fun tokenize(callback: (TokenizeResult) -> Unit) = onMain {
        whenFormSettles {
            val failed = phase as? Phase.Failed
            when {
                failed != null ->
                    callback(TokenizeResult.Failure(TokenizeError.local("form_unavailable", failed.message)))

                pendingTokenize != null ->
                    callback(TokenizeResult.Failure(TokenizeError.local("tokenize_in_progress", "This form is already tokenizing.")))

                else -> {
                    val commandId = UUID.randomUUID().toString()
                    pendingTokenize = commandId to callback
                    send(commandId, "tokenize", null)
                }
            }
        }
    }

    /** Ends the form and clears the card. Its fields go blank. Safe to call more than once. */
    fun close() = onMain {
        stopWatchingHost()
        surface?.stop()
        surface = null
        fields.forEach { it.get()?.stop() }
        fields.clear()
        settle(Phase.Failed("The card form was closed."))
        pendingTokenize?.second?.invoke(
            TokenizeResult.Failure(TokenizeError.local("form_unavailable", "The card form was closed."))
        )
        pendingTokenize = null
    }

    // Internal

    internal fun send(fieldCommand: String, elementType: CardElementType) = onMain {
        whenFormSettles {
            if (phase is Phase.Ready) send(UUID.randomUUID().toString(), fieldCommand, elementType)
        }
    }

    private fun send(commandId: String, name: String, elementType: CardElementType?) {
        val rootTag = surface?.rootTag ?: return
        runtime.send(
            Arguments.createMap().apply {
                putInt("rootTag", rootTag)
                putString("formId", formId)
                putString("commandId", commandId)
                putString("name", name)
                elementType?.let { putString("elementType", it.wire) }
            }
        )
    }

    /** The form cannot work: settles it failed and tells [onError], once. */
    private fun failWith(message: String) {
        if (phase !is Phase.Opening) return
        settle(Phase.Failed(message))
        onError?.invoke(CardFormError(message))
    }

    private fun whenFormSettles(work: () -> Unit) {
        if (phase is Phase.Opening) whenSettled.add(work) else work()
    }

    private fun settle(next: Phase) {
        if (phase !is Phase.Opening) return
        phase = next
        val waiting = whenSettled.toList()
        whenSettled.clear()
        waiting.forEach { it() }
    }

    private fun onEvent(name: String, payload: Map<String, Any?>) {
        when (name) {
            PaymentMethodsProtocol.FORM_READY -> {
                settle(Phase.Ready)
                onReady?.invoke()
            }

            PaymentMethodsProtocol.FORM_CHANGE -> {
                val next = CardFormState.from(payload)
                if (next == state) return
                state = next
                onChange?.invoke(next)
            }

            PaymentMethodsProtocol.FORM_ERROR ->
                failWith(payload["message"] as? String ?: "The card form could not be shown.")

            PaymentMethodsProtocol.COMMAND_RESULT -> {
                val pending = pendingTokenize ?: return
                if (pending.first != payload["commandId"]) return
                pendingTokenize = null
                pending.second(tokenizeResultOf(payload))
            }
        }
    }

    /**
     * A view has no moment at which it is known to be gone for good, and nothing else would
     * end the form, so the Activity's end does. The registration also keeps this form alive
     * for the Activity's life, so dropping your own reference does not strand a running form.
     */
    private fun closeWith(activity: Activity) {
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = close()
            })
        } else {
            activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(destroyed: Activity) {
                    if (destroyed !== activity) return
                    close()
                    activity.application.unregisterActivityLifecycleCallbacks(this)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            })
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}
