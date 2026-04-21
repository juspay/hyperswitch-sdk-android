package io.hyperswitch.demoapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.model.ElementConfiguration
import io.hyperswitch.model.ElementsUpdateResult
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.sdk.Hyperswitch
import io.hyperswitch.sdk.HyperswitchInstance
import io.hyperswitch.sdk.HyperInterface
import io.hyperswitch.sdk.PaymentSession
import io.hyperswitch.paymentsession.PMError
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.sdk.Elements
import io.hyperswitch.sdk.HyperswitchBoundElement
import io.hyperswitch.view.CVCWidget
import io.hyperswitch.view.PaymentElement
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WidgetActivity : AppCompatActivity(), HyperInterface {

    private lateinit var ctx: Activity

    private var sdkAuthorization: String = ""
    private var publishableKey: String = ""
    private var profileId: String = ""

    private lateinit var paymentElementBound: HyperswitchBoundElement
    private lateinit var cvcWidgetBound: HyperswitchBoundElement

    private lateinit var hyperswitchInstance: HyperswitchInstance
    private lateinit var elements: Elements
    private var paymentSession: PaymentSession? = null

    // Resolved from PaymentSession — not from server response
    private var lastUsedPaymentToken: String? = null
    private var lastUsedPaymentMethodId: String? = null
    private var defaultPaymentToken: String? = null
    private var defaultPaymentMethodId: String? = null

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun setStatus(message: String = "Could not connect to the server") {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = message
        }
    }

    private fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
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

    // ── Network ──────────────────────────────────────────────────────────────

    private fun getCL() {
        setButtonsEnabled(false)

        reset().get("http://10.0.2.2:5252/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val result = JSONObject(value ?: return)
                        sdkAuthorization = result.getString("sdkAuthorization")
                        publishableKey = result.getString("publishableKey")
                        profileId = result.optString("profileId")

                        runOnUiThread { initialiseWidgets() }
                    } catch (e: JSONException) {
                        setStatus("Error parsing server response")
                    }

                }

                override fun failure(error: FuelError) {
                    setStatus()
                }
            })
    }

    // ── Initialisation ───────────────────────────────────────────────────────


    private fun getCustomisations(): PaymentSheet.Configuration {
        /**
         *
         * Customisations
         *
         * */

        val primaryButtonShape = PaymentSheet.PrimaryButtonShape(32f, 0f)
        val address =
            PaymentSheet.Address.Builder().city("city").country("US").line1("US").line2("line2")
                .postalCode("560060").state("California").build()
        val billingDetails: PaymentSheet.BillingDetails =
            PaymentSheet.BillingDetails.Builder().address(address).email("email.com")
                .name("John Doe").phone("1234123443").build()
        val shippingDetails = AddressDetails("Shipping Inc.", address, "6205007614", true)

        val primaryButton = PaymentSheet.PrimaryButton(
            shape = primaryButtonShape,
        )
        val color1: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = "#8DBD00".toColorInt(),
            surface = "#F5F8F9".toColorInt(),
        )

        val color2: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = "#8DBD00".toColorInt(),
            surface = "#F5F8F9".toColorInt(),
        )

        val appearance: PaymentSheet.Appearance = PaymentSheet.Appearance(
            typography = PaymentSheet.Typography(
                sizeScaleFactor = 1f, fontResId = R.font.montserrat
            ),
            primaryButton = primaryButton,
            colorsLight = color1,
            colorsDark = color2,
            theme = PaymentSheet.Theme.Light
        )

        val configuration =
            PaymentSheet.Configuration.Builder("Example, Inc.")
                .appearance(appearance)
                .defaultBillingDetails(billingDetails).primaryButtonLabel("Purchase ($2.00)")
                .paymentSheetHeaderLabel("Select payment method")
                .savedPaymentSheetHeaderLabel("Payment methods").shippingDetails(shippingDetails)
                .allowsPaymentMethodsRequiringShippingAddress(false)
                .allowsDelayedPaymentMethods(true).displaySavedPaymentMethodsCheckbox(true)
                .displaySavedPaymentMethods(true).disableBranding(true).showVersionInfo(true)

        return configuration.build()
    }

    private fun initialiseWidgets() {
        hyperswitchInstance = Hyperswitch.init(
            activity = ctx,
            config = HyperswitchConfiguration(
                publishableKey = publishableKey
            )
        )

        val session = PaymentSessionConfiguration(sdkAuthorization)

        // Bind PaymentElement
        val paymentElement = findViewById<PaymentElement>(R.id.paymentElement)
        lifecycleScope.launch {
            elements = hyperswitchInstance
                .elements(session)
            paymentElementBound = elements.bind(paymentElement, getCustomisations())
        }


        // Init PaymentSession and fetch saved methods
        lifecycleScope.launch {
            paymentSession = hyperswitchInstance.initPaymentSession(session)
            paymentSession?.getCustomerSavedPaymentMethods { savedMethods ->
                savedMethods.getCustomerLastUsedPaymentMethodData().fold(
                    onSuccess = { data ->
                        lastUsedPaymentToken = data.paymentToken
                        lastUsedPaymentMethodId = data.paymentMethodId
                    },
                    onFailure = { error ->
                    }
                )
                // ── Default Saved ──
                savedMethods.getCustomerDefaultSavedPaymentMethodData().fold(
                    onSuccess = { data ->
                        defaultPaymentToken = data.paymentToken
                        defaultPaymentMethodId = data.paymentMethodId
                    },
                    onFailure = { /* no default set — that's fine */ }
                )
            }
            val cvcWidget = findViewById<CVCWidget>(R.id.cvcWidget)
            lifecycleScope.launch {
                cvcWidgetBound = elements
                    .bind(cvcWidget)
                runOnUiThread { setButtonsEnabled(true) }
            }

        }
    }


    private suspend fun updateIntent(): String {
        return suspendCancellableCoroutine { continuation ->
            reset().get("http://10.0.2.2:5252/update-payment", null)
                .responseString(object : Handler<String?> {
                    override fun success(value: String?) {
                        try {
                            val result = JSONObject(value ?: run {
                                continuation.cancel()
                                return
                            })
                            val auth = result.getString("sdkAuthorization")
                            sdkAuthorization = auth
                            continuation.resume(auth)
                        } catch (e: JSONException) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun failure(error: FuelError) {
                        continuation.resumeWithException(error.exception)
                    }
                })
        }
    }
    // ── Button wiring ────────────────────────────────────────────────────────

    private fun setupButtons() {
        findViewById<View>(R.id.reloadButton2).setOnClickListener { getCL() }

        findViewById<View>(R.id.confirmButton2).setOnClickListener { confirmPayment() }

        findViewById<View>(R.id.getLastUsedButton).setOnClickListener {
            val token = lastUsedPaymentToken
            val methodId = lastUsedPaymentMethodId
            if (token != null && methodId != null) {
                setStatus("Last Used → token: $token | methodId: $methodId")
            } else {
                setStatus("No last-used saved method available")
            }
        }

        findViewById<View>(R.id.getDefaultSavedMethodButton).setOnClickListener {
            val token = defaultPaymentToken
            val methodId = defaultPaymentMethodId
            if (token != null && methodId != null) {
                setStatus("Default → token: $token | methodId: $methodId")
            } else {
                setStatus("No default saved method available")
            }
        }

        findViewById<View>(R.id.updateIntent).setOnClickListener {
            elements.updateIntent(
                scope = lifecycleScope,
                sessionTokenProvider = { updateIntent() }
            ) { result ->
                when (result) {
                    is ElementsUpdateResult.Success -> {
                        // All elements updated — proceed to confirm
                        Log.i("Payment", "elements success")
                    }

                    is ElementsUpdateResult.TotalFailure -> {
                        // Token fetch failed or all elements failed
                        // Safe to retry the entire call
                        Log.e("Payment", "Total failure: ${result.cause.message}")
                    }

                    is ElementsUpdateResult.PartialFailure -> {
                        // Some elements live, some failed
                        Log.e("Payment", "Partial failure: ${result.failed.size} elements failed")

//                        if (result.canRetry) {
//                            elements.retry(
//                                scope = lifecycleScope,
//                                failedElements = result.failed,
//                                sessionTokenProvider = { updateIntent() }
//                            ) { retryResult ->
//                                when (retryResult) {
//                                    is ElementsUpdateResult.Success -> {
//                                        // All recovered
//
//                                    }
//                                    is ElementsUpdateResult.TotalFailure -> {
//                                        // Retry also failed entirely
//                                    }
//                                    is ElementsUpdateResult.PartialFailure -> {
//                                        // Some still failing after retry — surface to user
//                                    }
//                                }
//                            }
//                        }

                    }
                }
            }

        }

        findViewById<View>(R.id.confirmDefaultWithCVCButton).setOnClickListener {
            confirmDefaultUsed()
        }

        findViewById<View>(R.id.confirmLastUsedWithCVCButton).setOnClickListener {
            confirmLastUsed()
        }
    }

    // ── Payment actions ──────────────────────────────────────────────────────

    private fun confirmPayment() {
        lifecycleScope.launch {
            val result = paymentElementBound.confirmPayment()
            handlePaymentResult(result)
        }
    }

    private fun confirmLastUsed() {
        lifecycleScope.launch {
            cvcWidgetBound.confirmWithLastUsed { result ->
                handlePaymentResult(result)
            }
        }
    }

    private fun confirmDefaultUsed() {
        lifecycleScope.launch {
            cvcWidgetBound.confirmWithDefaultMethod { result ->
                handlePaymentResult(result)
            }
        }
    }

    private fun handlePaymentResult(result: PaymentResult) {
        runOnUiThread {
            when (result) {
                is PaymentResult.Completed -> toast("Payment Successful: ${result.data}")
                is PaymentResult.Failed -> toast("Payment Failed: ${result.throwable.message}")
                is PaymentResult.Canceled -> toast("Payment Cancelled")
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_activity)
        ctx = this
        setupButtons()
        getCL()
    }
}