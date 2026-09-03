package io.hyperswitch.demoapp.matrix

import android.app.Activity
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.sdk.Elements
import io.hyperswitch.sdk.HyperswitchBoundElement
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.view.CVCWidget
import io.hyperswitch.view.PaymentElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** What the Matrix screen provides to the runner. */
interface MatrixUi {
    fun log(line: String)
    /** Shows [message] with a Continue button. Returns false when 120 seconds pass without a press. */
    suspend fun gate(message: String): Boolean
    fun paymentContainer(index: Int): PaymentElement
    fun cvcContainer(): CVCWidget
    fun setStatus(id: String, status: String)
}

class Scenario(val id: String, val title: String, val run: suspend Ctx.() -> Unit)

class SessionRef(
    val name: String,
    var auth: String,
    val paymentId: String,
    val elements: Elements,
) {
    val bound = mutableListOf<HyperswitchBoundElement>()
}

class UpdateOutcome(val result: ElementsUpdateResult, val newAuth: String)

class ScenarioFailure(message: String) : RuntimeException(message)

/**
 * Shared state for every scenario: sessions by name, authorization labels, container ownership,
 * and the ledger window. One instance lives for the whole screen so chained scenarios can reuse
 * sessions.
 */
class Ctx(
    val activity: Activity,
    val backend: MatrixBackend,
    val ui: MatrixUi,
    private val instanceFor: suspend (MatrixBackend.IntentInfo) -> HyperswitchInstance,
    val configuration: PaymentSheet.Configuration,
) {
    val sessions = mutableMapOf<String, SessionRef>()
    val handlers = mutableMapOf<String, PaymentSessionHandler>()
    private val labels = mutableMapOf<String, String>()
    private val containerOwners = mutableMapOf<Int, Pair<SessionRef, HyperswitchBoundElement>>()
    private var windowStart = 0L
    private var window: List<MatrixBackend.LedgerRow> = emptyList()
    val failures = mutableListOf<String>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    suspend fun begin() {
        failures.clear()
        backend.clearFaults()
        windowStart = backend.ledgerLast()
        window = emptyList()
    }

    suspend fun end() {
        runCatching { backend.clearFaults() }
    }

    suspend fun reset() {
        containerOwners.keys.toList().forEach { releaseContainer(it) }
        sessions.clear()
        labels.clear()
        runCatching { backend.clearFaults() }
        runCatching { backend.clearLedger() }
        windowStart = 0L
        window = emptyList()
    }

    // ── Labels and logging ─────────────────────────────────────────────────────

    fun label(auth: String, name: String? = null): String {
        if (name != null) labels[auth] = name
        return labels.getOrPut(auth) { "?${labels.size + 1}" }
    }

    fun log(message: String) = ui.log(message)

    fun check(condition: Boolean, message: String) {
        if (condition) ui.log("  ok   $message") else {
            ui.log("  FAIL $message")
            failures += message
        }
    }

    fun fail(message: String): Nothing = throw ScenarioFailure(message)

    // ── Ledger ─────────────────────────────────────────────────────────────────

    suspend fun settle(minimumWaitMs: Long = 0): List<MatrixBackend.LedgerRow> {
        if (minimumWaitMs > 0) delay(minimumWaitMs)
        val started = System.currentTimeMillis()
        var lastNewAt = started
        var cursor = windowStart
        val collected = mutableListOf<MatrixBackend.LedgerRow>()
        while (true) {
            val (rows, last) = backend.ledger(cursor)
            if (rows.isNotEmpty()) {
                collected += rows
                cursor = last
                lastNewAt = System.currentTimeMillis()
            }
            val now = System.currentTimeMillis()
            if (now - lastNewAt >= 1_500 || now - started >= 35_000) break
            delay(500)
        }
        window = collected
        windowStart = cursor
        collected.forEach { row ->
            val auth = row.authorization?.let { label(it) } ?: "-"
            val fault = row.fault?.let { " fault=$it" } ?: ""
            ui.log("  ledger ${row.method} ${row.path.substringBefore('?')} auth=$auth$fault")
        }
        return collected
    }

    private fun kind(row: MatrixBackend.LedgerRow): String? {
        val path = row.path.substringBefore('?')
        return when {
            row.method == "GET" && Regex("^/payments/[^/]+/client$").matches(path) -> "client"
            row.method == "POST" && path == "/payments/session_tokens" -> "sessions"
            path.startsWith("/v1/sdk/configs/") -> "config"
            row.method == "POST" && Regex("^/payments/[^/]+/confirm$").matches(path) -> "confirm"
            else -> null
        }
    }

    fun rows(): List<MatrixBackend.LedgerRow> = window

    fun countsFor(auth: String): List<Int> {
        val counts = window.filter { it.authorization == auth }.groupingBy { kind(it) ?: "other" }.eachCount()
        return listOf("client", "sessions", "config", "confirm").map { counts[it] ?: 0 }
    }

    fun expectAuth(auth: String, client: Int = 0, sessions: Int = 0, config: Int = 0, confirm: Int = 0) {
        val actual = countsFor(auth)
        val expected = listOf(client, sessions, config, confirm)
        check(
            actual == expected,
            "${label(auth)} client/sessions/config/confirm expected ${expected.joinToString("/")} actual ${actual.joinToString("/")}",
        )
    }

    fun expect(session: SessionRef, client: Int = 0, sessions: Int = 0, config: Int = 0, confirm: Int = 0) =
        expectAuth(session.auth, client, sessions, config, confirm)

    // ── Sessions ───────────────────────────────────────────────────────────────

    suspend fun instance(intent: MatrixBackend.IntentInfo): HyperswitchInstance = instanceFor(intent)

    suspend fun newSession(name: String, amount: Int): SessionRef {
        val intent = backend.createIntent(amount)
        label(intent.sdkAuthorization, name)
        val elements = instance(intent).elements(PaymentSessionConfiguration(intent.sdkAuthorization))
        return SessionRef(name, intent.sdkAuthorization, intent.paymentId, elements).also { sessions[name] = it }
    }

    fun session(name: String): SessionRef = sessions[name] ?: fail("session $name was not created by an earlier scenario")

    // ── Widgets ────────────────────────────────────────────────────────────────

    suspend fun releaseContainer(index: Int) {
        val (session, bound) = containerOwners.remove(index) ?: return
        withContext(Dispatchers.Main) {
            runCatching { session.elements.unbind(bound) }
            runCatching { bound.destroy() }
        }
        session.bound -= bound
    }

    suspend fun bind(session: SessionRef, container: Int): HyperswitchBoundElement {
        releaseContainer(container)
        val view = ui.paymentContainer(container)
        val bound = withContext(Dispatchers.Main) { session.elements.bind(view, configuration) }
        session.bound += bound
        containerOwners[container] = session to bound
        return bound
    }

    /** Binds without releasing whatever is mounted, for the rebind-without-destroy scenario. */
    suspend fun bindWithoutRelease(session: SessionRef, container: Int): HyperswitchBoundElement =
        withContext(Dispatchers.Main) { session.elements.bind(ui.paymentContainer(container), configuration) }

    suspend fun bindCvc(session: SessionRef): HyperswitchBoundElement =
        withContext(Dispatchers.Main) { session.elements.bind(ui.cvcContainer()) }

    // ── SDK actions ────────────────────────────────────────────────────────────

    suspend fun updateIntent(session: SessionRef, amount: Int, newLabel: String): UpdateOutcome {
        var newAuth = ""
        val result = session.elements.updateIntent {
            newAuth = backend.updatePayment(session.paymentId, amount)
            label(newAuth, newLabel)
            PaymentSessionConfiguration(newAuth)
        }
        if (result is ElementsUpdateResult.Success && newAuth.isNotEmpty()) session.auth = newAuth
        return UpdateOutcome(result, newAuth)
    }

    suspend fun headlessGet(session: SessionRef): Result<PaymentSessionHandler> =
        runCatching { session.elements.getPaymentSession().getCustomerSavedPaymentMethods() }

    suspend fun confirmDefault(handler: PaymentSessionHandler, cvc: String? = "123"): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            handler.confirmWithCustomerDefaultPaymentMethod(cvc) { continuation.resume(it) }
        }

    suspend fun confirmLastUsed(handler: PaymentSessionHandler, cvc: String? = "123"): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            handler.confirmWithCustomerLastUsedPaymentMethod(cvc) { continuation.resume(it) }
        }

    suspend fun confirmToken(handler: PaymentSessionHandler, token: String, cvc: String? = "123"): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            handler.confirmWithCustomerPaymentToken(token, cvc) { continuation.resume(it) }
        }

    suspend fun confirmWithCvcWidget(handler: PaymentSessionHandler): PaymentResult =
        handler.confirmWithCustomerLastUsedPaymentMethod(ui.cvcContainer())

    suspend fun presentSheet(session: SessionRef): PaymentResult =
        session.elements.getPaymentSession().presentPaymentSheet(configuration)

    suspend fun gate(message: String) {
        ui.log("  gate: $message")
        if (!ui.gate(message)) fail("gate timed out: $message")
    }

    suspend fun sleep(ms: Long) = delay(ms)

    // ── Codes ──────────────────────────────────────────────────────────────────

    fun codeOf(throwable: Throwable?): String? = throwable?.cause?.message ?: throwable?.message

    fun codeOf(result: PaymentResult): String? = (result as? PaymentResult.Failed)?.throwable?.let { codeOf(it) }

    fun codeOf(result: ElementsUpdateResult): String? = (result as? ElementsUpdateResult.TotalFailure)?.cause?.let { codeOf(it) }

    fun isTerminal(result: PaymentResult): Boolean = result is PaymentResult.Completed || result is PaymentResult.Failed

    fun describe(result: PaymentResult): String = when (result) {
        is PaymentResult.Completed -> "Completed(${result.data})"
        is PaymentResult.Canceled -> "Canceled(${result.data})"
        is PaymentResult.Failed -> "Failed(${codeOf(result)})"
    }
}

class ScenarioRunner(private val ctx: Ctx) {

    suspend fun run(scenario: Scenario): Boolean {
        ctx.ui.setStatus(scenario.id, "running")
        ctx.log("── ${scenario.id} ${scenario.title}")
        val startedAt = System.currentTimeMillis()
        ctx.begin()
        val threw = runCatching { scenario.run(ctx) }.exceptionOrNull()
        ctx.end()
        if (threw != null) ctx.failures += "threw: ${threw.message}"
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        val passed = ctx.failures.isEmpty()
        val verdict = if (passed) "PASS ${scenario.id}" else "FAIL ${scenario.id}: ${ctx.failures.first()}"
        ctx.log("$verdict (${seconds}s)")
        ctx.ui.setStatus(scenario.id, if (passed) "PASS" else "FAIL")
        return passed
    }
}
