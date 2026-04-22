package io.hyperswitch.demoapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.CvcWidgetEvents
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.PaymentSessionHandler
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.sdk.Elements
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchBoundElement
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.view.CVCWidget
import io.hyperswitch.view.PaymentElement
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WidgetActivity : AppCompatActivity(), HyperInterface {

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    @Volatile private var sdkAuthorization: String = ""
    @Volatile private var paymentId: String = ""

    private var hyperswitchInstance: HyperswitchInstance? = null
    private var elements: Elements? = null

    private var paymentSessionHandler: PaymentSessionHandler? = null
    private var paymentElementBound: HyperswitchBoundElement? = null
    private var cvcWidgetBound: HyperswitchBoundElement? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_activity)
        setupButtons()
        fetchPaymentIntent()
    }

    // ── Network ────────────────────────────────────────────────────────────────────────────────

    private fun fetchPaymentIntent() {
        setButtonsEnabled(false)

        reset().get("$serverUrl/create-payment-intent")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = JSONObject(value ?: return)
                        val publishableKey  = json.getString("publishableKey")
                        val profileId       = json.optString("profileId")
                        sdkAuthorization    = json.getString("sdkAuthorization")
                        paymentId           = json.optString("paymentId")

                        runOnUiThread { initialiseWidgets(publishableKey, profileId) }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse server response", e)
                        setStatus("Error parsing server response")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "Backend request failed: ${error.message}")
                    setStatus("Could not connect to the server")
                }
            })
    }

    private suspend fun fetchUpdatedAuthorization(): PaymentSessionConfiguration =
        suspendCancellableCoroutine { continuation ->
            reset().post("$serverUrl/update-payment")
                .header("Content-Type" to "application/json")
                .body("""{"paymentId":"$paymentId", "currency": "HKD", "amount": 2999}""")
                .responseString(object : Handler<String?> {
                    override fun success(value: String?) {
                        try {
                            val auth = JSONObject(value ?: run { continuation.cancel(); return })
                                .getString("sdkAuthorization")
                            sdkAuthorization = auth
                            continuation.resume(PaymentSessionConfiguration(auth))
                        } catch (e: JSONException) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun failure(error: FuelError) {
                        continuation.resumeWithException(error.exception)
                    }
                })
        }

    // ── Initialisation ─────────────────────────────────────────────────────────────────────────

    private fun initialiseWidgets(publishableKey: String, profileId: String) {
        hyperswitchInstance = Hyperswitch.init(
            activity = this,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey,
                profileId = profileId,
            )
        )

        val sessionConfig = PaymentSessionConfiguration(sdkAuthorization)
        val paymentElement = findViewById<PaymentElement>(R.id.paymentElement)
        val cvcWidget = findViewById<CVCWidget>(R.id.cvcWidget)

        lifecycleScope.launch {
            // All bindings share one Elements session — initialise once, bind sequentially.
            elements = hyperswitchInstance?.elements(sessionConfig)
            paymentSessionHandler = elements?.getCustomerSavedPaymentMethods()
            paymentElementBound = elements?.bind(paymentElement, buildConfiguration())
            cvcWidgetBound      = elements?.bind(cvcWidget) {
                on(CvcWidgetEvents.CvcStatus) {
                    println(it)
                }
            }
            setButtonsEnabled(true)
        }
    }

    // ── Configuration ──────────────────────────────────────────────────────────────────────────

    private fun buildConfiguration(): PaymentSheet.Configuration {
        val address = PaymentSheet.Address.Builder()
            .city("city").country("US").line1("US").line2("line2")
            .postalCode("560060").state("California").build()

        val billingDetails = PaymentSheet.BillingDetails.Builder()
            .address(address).email("email.com").name("John Doe").phone("1234123443").build()

        val appearance = PaymentSheet.Appearance(
            typography = PaymentSheet.Typography(sizeScaleFactor = 1f, fontResId = R.font.montserrat),
            primaryButton = PaymentSheet.PrimaryButton(shape = PaymentSheet.PrimaryButtonShape(32f, 0f)),
            colorsLight = PaymentSheet.Colors(
                primary = "#8DBD00".toColorInt(),
                surface = "#F5F8F9".toColorInt(),
            ),
            colorsDark = PaymentSheet.Colors(
                primary = "#8DBD00".toColorInt(),
                surface = "#F5F8F9".toColorInt(),
            ),
            theme = PaymentSheet.Theme.Light,
        )

        return PaymentSheet.Configuration.Builder("Example, Inc.")
            .appearance(appearance)
            .defaultBillingDetails(billingDetails)
            .primaryButtonLabel("Purchase ($2.00)")
            .paymentSheetHeaderLabel("Select payment method")
            .savedPaymentSheetHeaderLabel("Payment methods")
            .allowsPaymentMethodsRequiringShippingAddress(false)
            .allowsDelayedPaymentMethods(true)
            .displaySavedPaymentMethodsCheckbox(true)
            .displaySavedPaymentMethods(true)
            .disableBranding(true)
            .showVersionInfo(true)
            .build()
    }

    // ── Button wiring ──────────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        findViewById<View>(R.id.reloadButton2).setOnClickListener {
            fetchPaymentIntent()
        }

        findViewById<View>(R.id.confirmButton2).setOnClickListener {
            lifecycleScope.launch {
                val result = paymentElementBound?.confirmPayment() ?: return@launch
                handleResult(result)
            }
        }

        findViewById<View>(R.id.getLastUsedButton).setOnClickListener {
            val data = paymentSessionHandler?.getCustomerLastUsedPaymentMethodData()
            setStatus("last used — $data")
        }

        findViewById<View>(R.id.getDefaultSavedMethodButton).setOnClickListener {
            val data = paymentSessionHandler?.getCustomerDefaultSavedPaymentMethodData()
            setStatus("default — $data")
        }

        findViewById<View>(R.id.updateIntent).setOnClickListener {
            lifecycleScope.launch {
                val result = elements?.updateIntent { fetchUpdatedAuthorization() }
                    ?: return@launch
                when (result) {
                    is ElementsUpdateResult.Success ->
                        Log.i(TAG, "Intent updated — all elements ready")

                    is ElementsUpdateResult.TotalFailure ->
                        setStatus("Update failed: ${result.cause.message}")

                    is ElementsUpdateResult.PartialFailure ->
                        setStatus("Partial update failure: ${result.failed.size} element(s) failed")
                }
            }
        }

        findViewById<View>(R.id.confirmDefaultWithCVCButton).setOnClickListener {
            lifecycleScope.launch {
                val cvcWidget = findViewById<CVCWidget>(R.id.cvcWidget)
                val result = paymentSessionHandler?.confirmWithCustomerDefaultPaymentMethod(cvcWidget)
                result?.let { handleResult(it) }
            }
        }

        findViewById<View>(R.id.confirmLastUsedWithCVCButton).setOnClickListener {
            lifecycleScope.launch {
                val cvcWidget = findViewById<CVCWidget>(R.id.cvcWidget)
                val result = paymentSessionHandler?.confirmWithCustomerLastUsedPaymentMethod(cvcWidget)
                result?.let { handleResult(it) }
            }
        }
    }

    // ── Result handling ────────────────────────────────────────────────────────────────────────

    private fun handleResult(result: PaymentResult) {
        val message = when (result) {
            is PaymentResult.Completed -> "Completed: ${result.data}"
            is PaymentResult.Canceled  -> "Cancelled: ${result.data}"
            is PaymentResult.Failed    -> "Failed: ${result.throwable.message.orEmpty()}"
        }
        setStatus(message)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────────────────────

    private fun setStatus(message: String) {
        runOnUiThread { findViewById<TextView>(R.id.resultText).text = message }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(
            R.id.confirmButton2,
            R.id.getLastUsedButton,
            R.id.getDefaultSavedMethodButton,
            R.id.confirmDefaultWithCVCButton,
            R.id.confirmLastUsedWithCVCButton,
        ).forEach { findViewById<View>(it).isEnabled = enabled }
    }

    // ── Constants ──────────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "WidgetActivity"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5252"
        private const val PREFS_NAME = "HyperswitchPrefs"
        private const val KEY_SERVER_URL = "server_url"
    }

    private fun loadServerUrl(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    private val serverUrl: String by lazy { loadServerUrl() }
}
