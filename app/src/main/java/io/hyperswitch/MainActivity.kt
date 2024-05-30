package io.hyperswitch

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentSheetResult
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity(), HyperInterface {

    private var paymentIntentClientSecret: String = "clientSecret"
    private var publishKey: String = ""

    lateinit var ctx: AppCompatActivity;

    private lateinit var paymentSheet: PaymentSheet
    private var configuration: PaymentSheet.Configuration? = null

    private fun setCustomisations() {
        /**
         *
         * Customisations
         *
         * */

        val primaryButtonShape = PaymentSheet.PrimaryButtonShape(0f, 0f)
        val address = PaymentSheet.Address.Builder()
            .city("city")
            .country("US")
            .line1("US")
            .line2("line2")
            .postalCode("560060")
            .state("California")
            .build()
        val billingDetails: PaymentSheet.BillingDetails? = PaymentSheet.BillingDetails.Builder()
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
            primaryButtonColorsLight,
            primaryButtonColorsDark,
            primaryButtonShape,
            null
        )
        val color1: PaymentSheet.Colors = PaymentSheet.Colors(
            primary= Color.BLACK,
            surface= Color.WHITE,
            component= Color.WHITE,
            componentBorder= Color.BLUE,
            componentDivider= Color.BLACK,
            onComponent= Color.BLACK,
            subtitle= Color.BLACK,
            onSurface= Color.BLACK,
            placeholderText= Color.GRAY,
            appBarIcon= Color.BLACK,
            error= Color.RED,
        )

        val color2: PaymentSheet.Colors = PaymentSheet.Colors(
            primary= Color.WHITE,
            surface= Color.DKGRAY,
            component= Color.BLUE,
            componentBorder= Color.BLUE,
            componentDivider= Color.WHITE,
            onComponent= Color.WHITE,
            subtitle= Color.WHITE,
            onSurface= Color.WHITE,
            placeholderText= Color.GRAY,
            appBarIcon= Color.WHITE,
            error= Color.RED,
        )

        val appearance: PaymentSheet.Appearance = PaymentSheet.Appearance(
            shapes = shapes,
            typography = null,
            primaryButton = null,
            locale = "en",
            colorsLight = color1,
            colorsDark = color2
        )

        val placeHolder = PaymentSheet.PlaceHolder(
            cardNumber = "**** **** **** 4242",
            expiryDate = "12/25",
            cvv = "***"
        )

        configuration = PaymentSheet.Configuration.Builder("Example, Inc.")
//            .appearance(appearance)
            .defaultBillingDetails(billingDetails)
            .googlePay(
                PaymentSheet.GooglePayConfiguration(
                    PaymentSheet.GooglePayConfiguration.Environment.Test,
                    "usa",
                    "dollar"
                )
            )
            .primaryButtonLabel("Add Payment Method")
            .paymentSheetHeaderLabel("Add a new Payment Method")
            .savedPaymentSheetHeaderLabel("Saved Payment Method")
            .shippingDetails(shippingDetails)
            .allowsPaymentMethodsRequiringShippingAddress(false)
            .allowsDelayedPaymentMethods(true)
            .displaySavedPaymentMethodsCheckbox(true)
            .displaySavedPaymentMethods(true)
            .placeHolder(placeHolder)
            .disableBranding(true)
            .netceteraSDKApiKey("YOUR_NETCETERA_API_KEY")
            .build()
    }


    private fun getCL() {

        ctx.findViewById<View>(R.id.reloadButton).isEnabled = false;
        ctx.findViewById<View>(R.id.launchButton).isEnabled = false;

        reset().get("http://10.0.2.2:5252/create-payment-intent", null)
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

                            PaymentConfiguration.init(applicationContext, publishKey)

                            ctx.runOnUiThread {
                                ctx.findViewById<View>(R.id.reloadButton).isEnabled = true
                                ctx.findViewById<View>(R.id.launchButton).isEnabled = true
                            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ctx = this

        /**
         *
         * Merchant API call to get Client Secret
         *
         * */
        getCL()
        findViewById<View>(R.id.reloadButton).setOnClickListener { getCL() }

        /**
         *
         * Initialise Payment Sheet
         *
         * */

        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)


        /**
         *
         * Launch Payment Sheet
         *
         * */


        findViewById<View>(R.id.launchButton).setOnClickListener {
            if (paymentIntentClientSecret == "clientSecret") {
                Toast.makeText(ctx, "Please wait ... \nFetching Client Secret ...", Toast.LENGTH_SHORT).show()
            } else {
                paymentSheet.presentWithPaymentIntent(paymentIntentClientSecret, configuration)
            }
        }

    }

    private fun setStatus(status: String, error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

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