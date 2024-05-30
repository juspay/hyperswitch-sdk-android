package io.hyperswitch

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import org.json.JSONException
import org.json.JSONObject

class MainFragment : Fragment() {

    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""

    private lateinit var paymentSheet: PaymentSheet
    private var configuration: PaymentSheet.Configuration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         *
         * Initialise Payment Sheet
         *
         * */

        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)
        getCL()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    private fun setCustomisations() {
        /**
         *
         * Customisations
         *
         * */

        val primaryButtonShape = PaymentSheet.PrimaryButtonShape(32f, 0f)
        val address = PaymentSheet.Address.Builder()
            .city("city")
            .country("US")
            .line1("US")
            .line2("line2")
            .postalCode("560060")
            .state("California")
            .build()
        val billingDetails: PaymentSheet.BillingDetails = PaymentSheet.BillingDetails.Builder()
            .address(address)
            .email("email.com")
            .name("John Doe")
            .phone("1234123443").build()
        val shippingDetails = AddressDetails("Shipping Inc.", address, "6205007614", true)
        val shapes = PaymentSheet.Shapes(10f, 1f, null)
        val primaryButtonColorsLight = PaymentSheet.PrimaryButtonColors(
            Color.BLACK,
            Color.WHITE,
            0
        )
        val primaryButtonColorsDark = PaymentSheet.PrimaryButtonColors(
            Color.WHITE,
            Color.BLACK,
            0
        )
        val primaryButton = PaymentSheet.PrimaryButton(
            shape = primaryButtonShape,
        )
        val color1: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = Color.parseColor("#8DBD00"),
            surface = Color.parseColor("#F5F8F9"),
        )

        val color2: PaymentSheet.Colors = PaymentSheet.Colors(
            primary = Color.parseColor("#8DBD00"),
            surface = Color.parseColor("#F5F8F9"),
        )

        val appearance: PaymentSheet.Appearance = PaymentSheet.Appearance(
            typography = PaymentSheet.Typography(sizeScaleFactor = 1f, fontResId = R.font.montserrat),
            primaryButton = primaryButton,
            colorsLight = color1,
            colorsDark = color2
        )

        val placeHolder = PaymentSheet.PlaceHolder(
            cardNumber = "**** **** **** 4242",
            expiryDate = "12/25",
            cvv = "***"
        )

        configuration = PaymentSheet.Configuration.Builder("Example, Inc.")
            .appearance(appearance)
            .defaultBillingDetails(billingDetails)
            .googlePay(
                PaymentSheet.GooglePayConfiguration(
                    PaymentSheet.GooglePayConfiguration.Environment.Test,
                    "usa",
                    "dollar"
                )
            )
            .primaryButtonLabel("Purchase ($2.00)")
            .paymentSheetHeaderLabel("Select payment method")
            .savedPaymentSheetHeaderLabel("Payment methods")
            .shippingDetails(shippingDetails)
            .allowsPaymentMethodsRequiringShippingAddress(false)
            .allowsDelayedPaymentMethods(true)
            .displaySavedPaymentMethodsCheckbox(true)
            .displaySavedPaymentMethods(true)
            .disableBranding(true)
            .netceteraSDKApiKey("YOUR_NETCETERA_API_KEY")
            .build()
    }

    private fun getCL() {

        Fuel.reset().get("http://10.0.2.2:5252/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            paymentIntentClientSecret = result.getString("clientSecret")
                            publishKey =  result.getString("publishableKey")
                            setCustomisations()

                            /**
                             *
                             * Initialise Payment Configuration
                             *
                             * */

                            PaymentConfiguration.init(requireContext(), publishKey)

                            paymentSheet.presentWithPaymentIntent(paymentIntentClientSecret, configuration)

                        }
                    } catch (e: JSONException) {
                        Log.d("Backend Response", e.toString())
                    }
                }

                override fun failure(error: FuelError) {
                    Log.d("Backend Response", error.toString())
                }
            })
    }

    private fun setStatus(status: String, error: String) {}

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when(paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                setStatus("Cancelled", paymentSheetResult.data)
            }
            is PaymentSheetResult.Failed -> {
                setStatus("Failed", paymentSheetResult.error.message ?: "")
            }
            is PaymentSheetResult.Completed -> {
                setStatus("Completed", paymentSheetResult.data)
            }
        }
    }

}