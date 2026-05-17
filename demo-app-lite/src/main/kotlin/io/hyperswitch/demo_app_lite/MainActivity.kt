package io.hyperswitch.demo_app_lite

import android.app.Activity
import android.content.Intent
import android.util.Patterns
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.PaymentSession
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.paymentsheet.PaymentResult
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

class MainActivity : Activity() {
    lateinit var ctx: Activity
    private var publishableKey: String = ""
    private var sdkAuthorization: String = ""
    private var netceteraApiKey: String? = null
    private val prefsName = "HyperswitchPrefs"
    private val keyServerUrl = "server_url"
    private var serverUrl = "http://10.0.2.2:5252"
    private lateinit var paymentSession: PaymentSession
    private lateinit var editText: EditText

    private fun fetchNetceteraApiKey() {
        reset().get("$serverUrl/netcetera-sdk-api-key").responseString(object : Handler<String?> {
            override fun success(value: String?) {
                try {
                    val result = value?.let { JSONObject(it) }
                    netceteraApiKey = result?.getString("netceteraApiKey")
                } catch (_: Exception) {
                }
            }

            override fun failure(error: FuelError) {}
        })
    }

    private fun getSharedPreferences(): android.content.SharedPreferences {
        return ctx.getSharedPreferences(prefsName, MODE_PRIVATE)
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences().edit { putString(keyServerUrl, url) }
    }

    private fun loadServerUrl(): String {
        return getSharedPreferences().getString(keyServerUrl, serverUrl)
            ?: serverUrl
    }

    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    private fun updateServerUrl(newUrl: String) {
        if (isValidUrl(newUrl)) {
            serverUrl = newUrl
            saveServerUrl(newUrl)
            setStatus("Reload Client Secret")
        } else {
            setStatus("Invalid URL format")
        }
    }

    private fun getCustomisations(): PaymentSheet.Configuration {
        val address = PaymentSheet.Address.Builder()
            .city("San Francisco")
            .country("US")
            .line1("123 Main St")
            .line2("Apt 4B")
            .postalCode("94102")
            .state("CA")
            .build()

        val billingDetails = PaymentSheet.BillingDetails.Builder()
            .address(address)
            .email("john@example.com")
            .name("John Doe")
            .phone("+919999999999")
            .build()

        val shippingDetails = AddressDetails(
            name               = "John Doe",
            address            = address,
            phoneNumber        = "+919999999999",
            isCheckboxSelected = true,
        )

        val appearance = PaymentSheet.Appearance(
            colorsLight = PaymentSheet.Colors(
                primary                    = "#006DF9".toColorInt(),
                surface                    = "#FFFFFF".toColorInt(),
                component                  = "#F6F8F9".toColorInt(),
                componentBorder            = "#E0E0E0".toColorInt(),
                componentDivider           = "#E0E0E0".toColorInt(),
                onComponent                = "#000000".toColorInt(),
                onSurface                  = "#000000".toColorInt(),
                subtitle                   = "#767676".toColorInt(),
                placeholderText            = "#9E9E9E".toColorInt(),
                appBarIcon                 = "#000000".toColorInt(),
                error                      = "#FF0000".toColorInt(),
                loaderBackground           = "#F6F8F9".toColorInt(),
                loaderForeground           = "#006DF9".toColorInt(),
                selectedComponentBackground = "#EBF2FF".toColorInt(),
                selectedComponentBorder    = "#006DF9".toColorInt(),
                selectedComponentBorderWidth = 2f,
                selectedComponentDivider   = "#E0E0E0".toColorInt(),
                selectedComponentText      = "#000000".toColorInt(),
            ),
            colorsDark = PaymentSheet.Colors(
                primary                    = "#006DF9".toColorInt(),
                surface                    = "#FFFFFF".toColorInt(),
                component                  = "#F6F8F9".toColorInt(),
                componentBorder            = "#E0E0E0".toColorInt(),
                componentDivider           = "#E0E0E0".toColorInt(),
                onComponent                = "#000000".toColorInt(),
                onSurface                  = "#000000".toColorInt(),
                subtitle                   = "#767676".toColorInt(),
                placeholderText            = "#9E9E9E".toColorInt(),
                appBarIcon                 = "#000000".toColorInt(),
                error                      = "#FF0000".toColorInt(),
                loaderBackground           = "#F6F8F9".toColorInt(),
                loaderForeground           = "#006DF9".toColorInt(),
                selectedComponentBackground = "#1a3a5c".toColorInt(),
                selectedComponentBorder    = "#0057c7".toColorInt(),
                selectedComponentBorderWidth = 2f,
                selectedComponentDivider   = "#e6e6e6".toColorInt(),
                selectedComponentText      = "#ffffff".toColorInt(),
            ),
            shapes = PaymentSheet.Shapes(
                cornerRadiusDp      = 8f,
                borderStrokeWidthDp = 1f,
                shadow = PaymentSheet.Shadow(
                    color     = "#000000".toColorInt(),
                    intensity = 4f,
                ),
            ),
            typography = PaymentSheet.Typography(
                sizeScaleFactor = 1f,
                fontFamily      = "Roboto",
            ),
            primaryButton = PaymentSheet.PrimaryButton(
                colorsLight = PaymentSheet.PrimaryButtonColors(
                    background   = "#FFE500".toColorInt(),
                    onBackground = "#000000".toColorInt(),
                    border       = "#000000".toColorInt(),
                ),
                colorsDark = PaymentSheet.PrimaryButtonColors(
                    background   = "#FFE500".toColorInt(),
                    onBackground = "#000000".toColorInt(),
                    border       = "#000000".toColorInt(),
                ),
                shape = PaymentSheet.PrimaryButtonShape(
                    cornerRadiusDp      = 8f,
                    borderStrokeWidthDp = 2.5f,
                    shadow = PaymentSheet.Shadow(
                        color     = "#000000".toColorInt(),
                        intensity = 4f,
                    ),
                ),
            ),
            locale = "en",
            theme  = PaymentSheet.Theme.Light,
        )

        val wallets = PaymentSheet.WalletConfiguration(
            googlePay = PaymentSheet.GooglePayWalletConfig(
                visibility       = PaymentSheet.WalletShowType.Auto,
                buttonType       = PaymentSheet.GooglePayButtonType.BUY,
                buttonStyleLight = PaymentSheet.GooglePayButtonStyle.Dark,
                buttonStyleDark  = PaymentSheet.GooglePayButtonStyle.Dark,
            ),
        )

        return PaymentSheet.Configuration.Builder("Example, Inc.")
            .appearance(appearance)
            .wallets(wallets)
            .placeHolder(
                PaymentSheet.PlaceHolder(
                    cardNumber = "4242 4242 4242 4242",
                    expiryDate = "MM / YY",
                    cvv        = "CVC",
                )
            )
            .defaultBillingDetails(billingDetails)
            .shippingDetails(shippingDetails)
            .customer(
                PaymentSheet.CustomerConfiguration(
                    id                 = "cus_xxxxxxxxxxxx",
                    ephemeralKeySecret = "ephem_xxxxxxxxxxxx",
                )
            )
            .primaryButtonLabel("Pay Now")
            .paymentSheetHeaderLabel("Select a payment method")
            .savedPaymentSheetHeaderLabel("Saved payment method")
            .allowsDelayedPaymentMethods(true)
            .allowsPaymentMethodsRequiringShippingAddress(false)
            .displaySavedPaymentMethodsCheckbox(true)
            .displaySavedPaymentMethods(true)
            .displayDefaultSavedPaymentIcon(true)
            .disableBranding(true)
            .stickyPayButton(true)
            .redirectionInfo("hidden")
            .paymentMethodOrder(
                listOf("apple_pay", "google_pay", "paypal", "samsung_pay", "klarna", "credit")
            )
            .paymentMethodsConfig(
                listOf(
                    PaymentSheet.PaymentMethodConfig(paymentMethod = "card",   message = ""),
                    PaymentSheet.PaymentMethodConfig(paymentMethod = "wallet", message = ""),
                )
            )
            .showVersionInfo(true)
            .also { builder -> netceteraApiKey?.let { builder.netceteraSDKApiKey(it) } }
            .build()
    }

    private fun getCL() {

        ctx.findViewById<View>(R.id.launchButton).isEnabled = false

        reset().get("$serverUrl/create-payment-intent", null)
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        Log.d("Backend Response", value.toString())

                        val result = value?.let { JSONObject(it) }
                        if (result != null) {
                            sdkAuthorization = result.getString("sdkAuthorization")
                            publishableKey = result.getString("publishableKey")

                            /**
                             *
                             * Create Payment Session Object
                             *
                             * */

                            paymentSession = PaymentSession(ctx, publishableKey)

                            /**
                             *
                             * Initialise Payment Session
                             *
                             * */

                            paymentSession.initPaymentSession(sdkAuthorization)

                            ctx.runOnUiThread {
                                ctx.findViewById<View>(R.id.launchButton).isEnabled = true
                            }
                        }
                    } catch (e: JSONException) {
                        Log.d("Backend Response", e.toString())
                        setStatus("could not connect to the server")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.d("Backend Response", error.message ?: "")
                    setStatus("could not connect to the server")
                }
            })

        fetchNetceteraApiKey()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ctx = this
        editText = ctx.findViewById(R.id.ipAddressInput)
        serverUrl = loadServerUrl()
        editText.setText(serverUrl)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { newUrl ->
                    if (newUrl.isNotEmpty()) {
                        updateServerUrl(newUrl)
                    }
                }
            }
        })

        /**
         *
         * Merchant API call to get Client Secret
         *
         * */

        getCL()
        findViewById<View>(R.id.reloadButton).setOnClickListener {
            getCL()
        }

        /**
         *
         * Launch Payment Sheet
         *
         * */

        findViewById<View>(R.id.launchButton).setOnClickListener {
            val customisations = getCustomisations()
            paymentSession.presentPaymentSheet(customisations, ::onPaymentResult)
        }

        findViewById<View>(R.id.c2pButton).setOnClickListener {
            startActivity(Intent(this, ClickToPayExample::class.java))
        }
    }

    private fun setStatus(error: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text = error
        }
    }

    private fun onPaymentResult(paymentResult: PaymentResult) {
        when (paymentResult) {
            is PaymentResult.Canceled -> {
                setStatus(paymentResult.data)
            }

            is PaymentResult.Failed -> {
                setStatus(paymentResult.throwable.message ?: "")
            }

            is PaymentResult.Completed -> {
                setStatus(paymentResult.data)
            }
        }
    }
}