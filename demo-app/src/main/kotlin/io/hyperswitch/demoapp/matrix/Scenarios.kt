package io.hyperswitch.demoapp.matrix

import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object Scenarios {

    val all: List<Scenario> = listOf(
        Scenario("M1", "Init prefetches once") {
            val a = newSession("A", 1001)
            settle(); expect(a, client = 1, sessions = 1, config = 1)
        },
        Scenario("M2", "Bind uses the cache") {
            val a = session("A")
            bind(a, 1)
            settle(); expect(a)
        },
        Scenario("M3", "Two sessions stay isolated") {
            val a = session("A")
            val b = newSession("B", 2002)
            settle(); expect(b, client = 1, sessions = 1, config = 1); expect(a)
            bind(b, 2)
            settle(); expect(b); expect(a)
        },
        Scenario("M4", "One session, two widgets") {
            val c = newSession("C", 2503)
            settle(); expect(c, client = 1, sessions = 1, config = 1)
            bind(c, 2); settle(); expect(c)
            bind(c, 3); settle(); expect(c)
        },
        Scenario("M13", "updateIntent on a two-widget session") {
            val c = session("C")
            val outcome = updateIntent(c, 2504, "C'")
            check(outcome.result is ElementsUpdateResult.Success, "updateIntent result Success (got ${codeOf(outcome.result) ?: outcome.result::class.simpleName})")
            settle(); expectAuth(outcome.newAuth, client = 1, sessions = 1, config = 1)
        },
        Scenario("M5", "updateIntent with widgets") {
            val a = session("A")
            val b = session("B")
            val outcome = updateIntent(a, 3003, "A'")
            check(outcome.result is ElementsUpdateResult.Success, "updateIntent result Success (got ${codeOf(outcome.result) ?: outcome.result::class.simpleName})")
            settle(); expectAuth(outcome.newAuth, client = 1, sessions = 1, config = 1); expect(b)
            bind(a, 3)
            settle(); expect(a)
        },
        Scenario("M6", "updateIntent without widgets") {
            val d = newSession("D", 4004)
            settle()
            val outcome = updateIntent(d, 4005, "D'")
            check(outcome.result is ElementsUpdateResult.Success, "updateIntent result Success")
            settle(); expectAuth(outcome.newAuth, client = 1, sessions = 1, config = 1)
            check(d.elements.getPaymentSession().getSdkAuthorization() == outcome.newAuth, "session authorization equals D'")
        },
        Scenario("M7", "Duplicate init is rejected") {
            val intent = backend.createIntent(5005)
            label(intent.sdkAuthorization, "E")
            val inst = instance(intent)
            val results = coroutineScope {
                (1..2).map { async { runCatching { inst.initPaymentSession(PaymentSessionConfiguration(intent.sdkAuthorization)) } } }.awaitAll()
            }
            val successes = results.count { it.isSuccess }
            val duplicates = results.count { codeOf(it.exceptionOrNull()) == "SESSION_INIT_IN_PROGRESS" }
            check(successes == 1 && duplicates == 1, "one Success and one SESSION_INIT_IN_PROGRESS (got $successes success, $duplicates duplicate)")
            settle(); expectAuth(intent.sdkAuthorization, client = 1, sessions = 1, config = 1)
        },
        Scenario("M8", "Destroy and rebind") {
            val a = session("A")
            releaseContainer(1)
            val f = newSession("F", 6006)
            settle(); expectAuth(f.auth, client = 1, sessions = 1, config = 1); expect(a)
            bind(f, 1)
            settle(); expect(f); expect(a)
        },
        Scenario("M9", "Rebind without destroy (manual)") {
            val f = session("F")
            val g = newSession("F2", 6007)
            settle()
            bindWithoutRelease(g, 1)
            settle()
            log("  MANUAL: check logcat for 'setSdkAuthorization ignored' from PaymentWidgetView")
            check(true, "manual check recorded; verdict is the logcat line")
            releaseContainer(1); bind(f, 1); settle()
        },
        Scenario("M10", "updateIntent prefetch times out") {
            val l = newSession("L", 4014)
            settle()
            bind(l, 2); settle()
            backend.addFault("/client", "*", "delay", 40_000)
            val first = updateIntent(l, 4015, "L'")
            check(codeOf(first.result) == "PREFETCH_FAILED", "first updateIntent is PREFETCH_FAILED (got ${codeOf(first.result) ?: first.result::class.simpleName})")
            check(l.elements.getPaymentSession().getSdkAuthorization() != first.newAuth, "session still on L")
            gate("Confirm the widget in container 2 shows the 40.14 intent with no overlay, then press Continue")
            settle(15_000); expectAuth(first.newAuth, client = 1, sessions = 1, config = 1)
            val second = updateIntent(l, 4016, "L''")
            check(second.result is ElementsUpdateResult.Success, "second updateIntent Success")
            settle(); expectAuth(second.newAuth, client = 1, sessions = 1, config = 1)
        },
        Scenario("M11", "Init prefetch times out, late completion repopulates") {
            backend.addFault("/client", "*", "delay", 40_000)
            val startedAt = System.currentTimeMillis()
            val n = newSession("N", 4024)
            val seconds = (System.currentTimeMillis() - startedAt) / 1000
            check(seconds in 28..36, "init returned after about 30 seconds (got ${seconds}s)")
            bind(n, 3)
            settle(15_000); expect(n, client = 2, sessions = 2, config = 2)
            bind(n, 2)
            settle(); expect(n)
        },
        Scenario("M12", "Concurrent updateIntent is rejected") {
            val p = newSession("P", 4034)
            settle(); bind(p, 2); settle()
            val outcomes = coroutineScope {
                listOf(4035 to "P'", 4036 to "P''").map { (amount, name) -> async { updateIntent(p, amount, name) } }.awaitAll()
            }
            val successes = outcomes.filter { it.result is ElementsUpdateResult.Success }
            val rejected = outcomes.count { codeOf(it.result) == "ALREADY_IN_PROGRESS" }
            check(successes.size == 1 && rejected == 1, "one Success and one ALREADY_IN_PROGRESS (got ${successes.size} success, $rejected rejected)")
            settle()
            successes.firstOrNull()?.let { expectAuth(it.newAuth, client = 1, sessions = 1, config = 1) }
        },
        Scenario("M14", "Partial prefetch failure at init") {
            backend.addFault("/session_tokens", "*", "error", 500)
            val q = newSession("Q", 4044)
            settle(); expect(q, client = 1, sessions = 1, config = 1)
            check(rows().any { it.fault == "error" }, "the sessions row was faulted")
            bind(q, 3)
            settle(); expect(q, sessions = 1)
        },
        Scenario("M15", "updateIntent prefetch API error falls back") {
            val r = newSession("R", 4054)
            settle(); bind(r, 2); settle()
            backend.addFault("/client", "*", "error", 500)
            val outcome = updateIntent(r, 4055, "R'")
            check(outcome.result is ElementsUpdateResult.Success, "updateIntent Success despite the faulted prefetch")
            settle(); expectAuth(outcome.newAuth, client = 2, sessions = 1, config = 1)
        },
        Scenario("H1", "Headless get from cache") {
            val a = session("A")
            val handler = headlessGet(a).getOrElse { fail("get failed: ${codeOf(it)}") }
            val methods = handler.getCustomerSavedPaymentMethodData()
            check(methods.isSuccess && methods.getOrThrow().isNotEmpty(), "handler has saved methods (${methods.exceptionOrNull()?.let { codeOf(it) } ?: "ok"})")
            handlers["A"] = handler
            settle(); expect(a)
        },
        Scenario("H2", "Confirm by token, then cache cleared") {
            val a = session("A")
            val handler = handlers["A"] ?: fail("run H1 first")
            val result = confirmLastUsed(handler)
            check(isTerminal(result), "terminal result (${describe(result)})")
            settle(); expect(a, confirm = 1)
            headlessGet(a)
            settle(); expect(a, client = 1)
        },
        Scenario("H3", "Retry on a used handler") {
            val handler = handlers["A"] ?: fail("run H1 first")
            val result = confirmLastUsed(handler)
            check(codeOf(result) == "HANDLER_ALREADY_USED", "HANDLER_ALREADY_USED (got ${describe(result)})")
        },
        Scenario("H4", "Stale handler after update") {
            val g = newSession("G", 4064)
            settle()
            val handler = headlessGet(g).getOrElse { fail("get failed: ${codeOf(it)}") }
            val outcome = updateIntent(g, 4065, "G'")
            check(outcome.result is ElementsUpdateResult.Success, "updateIntent Success")
            settle()
            val result = confirmLastUsed(handler)
            check(codeOf(result) == "STALE_PAYMENT_SESSION_HANDLER", "STALE_PAYMENT_SESSION_HANDLER (got ${describe(result)})")
            settle(); expect(g)
        },
        Scenario("H5", "CVC widget confirm") {
            val j = newSession("J", 4074)
            settle()
            bindCvc(j)
            val handler = headlessGet(j).getOrElse { fail("get failed: ${codeOf(it)}") }
            settle()
            gate("Type CVC 123 into the CVC widget, then press Continue")
            val result = confirmWithCvcWidget(handler)
            check(isTerminal(result), "terminal result (${describe(result)})")
            settle(); expect(j, confirm = 1)
        },
        Scenario("H6", "Cancel keeps the cache") {
            val k = newSession("K", 4084)
            settle()
            log("  dismiss the payment sheet without paying")
            val result = presentSheet(k)
            check(result is PaymentResult.Canceled, "Canceled (got ${describe(result)})")
            settle()
            headlessGet(k)
            settle(); expect(k)
        },
        Scenario("H7", "Losing a confirm race keeps the handler usable") {
            val s = newSession("S", 4094)
            settle()
            val h1 = headlessGet(s).getOrElse { fail("first get failed: ${codeOf(it)}") }
            val h2 = headlessGet(s).getOrElse { fail("second get failed: ${codeOf(it)}") }
            settle()
            backend.addFault("/confirm", s.auth, "delay", 8_000)
            val firstResult = coroutineScope {
                val first = async { confirmLastUsed(h1) }
                sleep(1_000)
                val second = confirmLastUsed(h2)
                check(codeOf(second) == "ALREADY_IN_PROGRESS", "h2 first attempt ALREADY_IN_PROGRESS (got ${describe(second)})")
                first.await()
            }
            check(isTerminal(firstResult), "h1 terminal (${describe(firstResult)})")
            val third = confirmLastUsed(h2)
            check(isTerminal(third), "h2 second attempt terminal, not ALREADY_IN_PROGRESS (${describe(third)})")
            settle(); expect(s, confirm = 2)
        },
        Scenario("H8", "Headless request timeout and duplicate get") {
            backend.addFault("/client", "*", "error", 500)
            val t = newSession("T", 4104)
            settle()
            backend.addFault("/client", "*", "delay", 40_000)
            val outcome = coroutineScope {
                val first = async { headlessGet(t) }
                sleep(2_000)
                val second = headlessGet(t)
                check(codeOf(second.exceptionOrNull()) == "ALREADY_IN_PROGRESS", "second get ALREADY_IN_PROGRESS (got ${codeOf(second.exceptionOrNull())})")
                first.await()
            }
            check(codeOf(outcome.exceptionOrNull()) == "HEADLESS_TIMEOUT", "first get HEADLESS_TIMEOUT (got ${codeOf(outcome.exceptionOrNull())})")
            settle(15_000); expect(t, client = 2, sessions = 1, config = 1)
            log("  MANUAL: check logcat for 'dropping late response' from HyperHeadlessModule")
        },
        Scenario("H9", "Two sessions headless in parallel") {
            val u = newSession("U", 4114)
            val v = newSession("V", 4115)
            settle()
            val (hu, hv) = coroutineScope { listOf(async { headlessGet(u) }, async { headlessGet(v) }).awaitAll() }
            val handlerU = hu.getOrElse { fail("U get failed: ${codeOf(it)}") }
            val handlerV = hv.getOrElse { fail("V get failed: ${codeOf(it)}") }
            val (ru, rv) = coroutineScope { listOf(async { confirmLastUsed(handlerU) }, async { confirmLastUsed(handlerV) }).awaitAll() }
            check(isTerminal(ru) && isTerminal(rv), "both confirms terminal (${describe(ru)}, ${describe(rv)})")
            settle(); expect(u, confirm = 1); expect(v, confirm = 1)
        },
        Scenario("S1", "Sheet payment clears the cache") {
            val w = newSession("W", 4124)
            settle()
            log("  pay with 4242 4242 4242 4242, any future expiry, CVC 123")
            val result = presentSheet(w)
            check(result is PaymentResult.Completed, "Completed (got ${describe(result)})")
            settle()
            headlessGet(w)
            settle(); expect(w, client = 1)
        },
        Scenario("W1", "Widget payment clears the cache") {
            val x = newSession("X", 4134)
            settle()
            val bound = bind(x, 2)
            settle()
            gate("Enter 4242 4242 4242 4242, any future expiry, CVC 123 in the widget in container 2, then press Continue")
            val result = bound.confirmPayment()
            check(result is PaymentResult.Completed, "Completed (got ${describe(result)})")
            settle()
            bind(x, 3)
            settle(); expect(x, client = 1)
        },
        Scenario("E1", "Eviction by count") {
            val created = mutableListOf<SessionRef>()
            for (index in 1..6) {
                val created6 = newSession("I$index", 5100 + index)
                settle(); expect(created6, client = 1, sessions = 1, config = 1)
                created += created6
            }
            bind(created.first(), 2)
            settle(); expect(created.first(), client = 1)
            bind(created.last(), 3)
            settle(); expect(created.last())
        },
    )
}
