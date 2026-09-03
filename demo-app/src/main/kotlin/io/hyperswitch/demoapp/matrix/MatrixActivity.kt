package io.hyperswitch.demoapp.matrix

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.hyperswitch.demoapp.R
import io.hyperswitch.demoapp.buildDemoConfiguration
import io.hyperswitch.model.CustomEndpointConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.HyperswitchEnvironment
import io.hyperswitch.model.OverrideEndpoints
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.view.CVCWidget
import io.hyperswitch.view.PaymentElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MatrixActivity : AppCompatActivity(), HyperInterface, MatrixUi {

    private lateinit var backend: MatrixBackend
    private lateinit var ctx: Ctx
    private lateinit var runner: ScenarioRunner
    private var instance: HyperswitchInstance? = null
    private var gateDeferred: CompletableDeferred<Boolean>? = null
    private var running: Job? = null
    private val statusViews = mutableMapOf<String, TextView>()
    private val logLines = StringBuilder()

    private val serverUrl: String by lazy {
        getSharedPreferences("HyperswitchPrefs", MODE_PRIVATE)
            .getString("server_url", "http://10.0.2.2:5252") ?: "http://10.0.2.2:5252"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.matrix_activity)
        backend = MatrixBackend(serverUrl)
        ctx = Ctx(
            activity = this,
            backend = backend,
            ui = this,
            instanceFor = { intent -> instanceFor(intent.publishableKey, intent.profileId) },
            configuration = buildDemoConfiguration(),
        )
        runner = ScenarioRunner(ctx)
        buildScenarioRows()
        findViewById<View>(R.id.runAllButton).setOnClickListener { runScenarios(Scenarios.all) }
        findViewById<View>(R.id.resetButton).setOnClickListener { resetAll() }
        findViewById<View>(R.id.copyLogButton).setOnClickListener { copyLog() }
        findViewById<View>(R.id.continueButton).setOnClickListener { gateDeferred?.complete(true) }
        log("server: $serverUrl (proxy at $serverUrl/proxy)")
    }

    private fun instanceFor(publishableKey: String, profileId: String): HyperswitchInstance =
        instance ?: Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
                customConfig = CustomEndpointConfiguration(
                    overrideEndpoints = OverrideEndpoints(customBackendEndpoint = "$serverUrl/proxy"),
                ),
                environment = HyperswitchEnvironment.SANDBOX,
            ),
        ).also { instance = it }

    // ── Scenario list ─────────────────────────────────────────────────────────

    private fun buildScenarioRows() {
        val list = findViewById<LinearLayout>(R.id.scenarioList)
        Scenarios.all.forEach { scenario ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val title = TextView(this).apply {
                text = "${scenario.id}  ${scenario.title}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val status = TextView(this).apply {
                text = "idle"
                minWidth = 140
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val run = Button(this).apply {
                text = "Run"
                setOnClickListener { runScenarios(listOf(scenario)) }
            }
            statusViews[scenario.id] = status
            row.addView(title); row.addView(status); row.addView(run)
            list.addView(row)
        }
    }

    private fun runScenarios(scenarios: List<Scenario>) {
        if (running?.isActive == true) {
            log("a run is already in progress")
            return
        }
        running = lifecycleScope.launch {
            scenarios.forEach { runner.run(it) }
            log("run finished")
        }
    }

    private fun resetAll() {
        running?.cancel()
        running = null
        gateDeferred?.complete(false)
        lifecycleScope.launch {
            ctx.reset()
            statusViews.values.forEach { it.text = "idle" }
            logLines.clear()
            findViewById<TextView>(R.id.logText).text = ""
            log("reset done")
        }
    }

    private fun copyLog() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("matrix log", logLines.toString()))
        log("log copied")
    }

    // ── MatrixUi ───────────────────────────────────────────────────────────────

    override fun log(line: String) {
        runOnUiThread {
            logLines.append(line).append('\n')
            val view = findViewById<TextView>(R.id.logText)
            view.text = logLines
            findViewById<ScrollView>(R.id.logScroll).post {
                findViewById<ScrollView>(R.id.logScroll).fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override suspend fun gate(message: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        gateDeferred = deferred
        runOnUiThread {
            findViewById<TextView>(R.id.gateText).apply { text = message; visibility = View.VISIBLE }
            findViewById<View>(R.id.continueButton).visibility = View.VISIBLE
        }
        val pressed = withTimeoutOrNull(120_000) { deferred.await() } ?: false
        runOnUiThread {
            findViewById<View>(R.id.gateText).visibility = View.GONE
            findViewById<View>(R.id.continueButton).visibility = View.GONE
        }
        gateDeferred = null
        return pressed
    }

    override fun paymentContainer(index: Int): PaymentElement = when (index) {
        1 -> findViewById(R.id.container1)
        2 -> findViewById(R.id.container2)
        else -> findViewById(R.id.container3)
    }

    override fun cvcContainer(): CVCWidget = findViewById(R.id.cvcContainer)

    override fun setStatus(id: String, status: String) {
        runOnUiThread { statusViews[id]?.text = status }
    }
}
