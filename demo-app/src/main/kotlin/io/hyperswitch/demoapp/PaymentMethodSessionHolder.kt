package io.hyperswitch.demoapp

import io.hyperswitch.paymentmethods.CardForm
import io.hyperswitch.paymentmethods.PaymentMethodSession
import io.hyperswitch.sdk.HyperswitchInstance

/**
 * Holds a live [PaymentMethodSession]/[CardForm] across activities within this process.
 *
 * Demonstrates that `initPaymentMethodSession`/`createCardForm` can run in one activity
 * ([PaymentMethodsInitActivity]) while the actual input field views are bound and shown in a
 * completely different one ([PaymentMethodsViewActivity]) — the [CardForm] instance itself is
 * just a plain object, not tied to the activity that created it.
 *
 * In-memory only: lost on process death, same as any other in-memory singleton. A real app
 * would re-init rather than try to persist these across a process restart.
 */
object PaymentMethodSessionHolder {
    var hyperswitchInstance: HyperswitchInstance? = null
    var paymentMethodSession: PaymentMethodSession? = null
    var cardForm: CardForm? = null

    fun clear() {
        cardForm?.release()
        paymentMethodSession?.release()
        cardForm = null
        paymentMethodSession = null
        hyperswitchInstance = null
    }
}
