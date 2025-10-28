package io.hyperswitch.paymentsheet

import android.app.Activity
import android.app.Fragment
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.annotation.RequiresApi
import kotlinx.parcelize.Parcelize

/**
 * A drop-in class that presents a bottom sheet to collect and process a customer's payment.
 */
class PaymentSheet internal constructor(
    private val paymentSheetLauncher: PaymentSheetLauncher
) {

    /**
     * Constructor to be used when launching the payment sheet from an Activity.
     *
     * @param activity  the Activity that is presenting the payment sheet.
     * @param callback  called with the result of the payment after the payment sheet is dismissed.
     */
    constructor(
        activity: Activity,
        callback: PaymentSheetResultCallback
    ) : this(
        DefaultPaymentSheetLauncher(activity, callback)
    )

    /**
     * Constructor to be used when launching the payment sheet from a Fragment.
     *
     * @param fragment the Fragment that is presenting the payment sheet.
     * @param callback called with the result of the payment after the payment sheet is dismissed.
     */
    constructor(
        fragment: Fragment,
        callback: PaymentSheetResultCallback
    ) : this(
        DefaultPaymentSheetLauncher(fragment, callback)
    )

    /**
     * Present the payment sheet to process a [PaymentIntent].
     * If the [PaymentIntent] is already confirmed, [PaymentSheetResultCallback] will be invoked
     * with [PaymentSheetResult.Completed].
     *
     * @param paymentIntentClientSecret the client secret for the [PaymentIntent].
     * @param configuration optional [PaymentSheet] settings.
     */
    @JvmOverloads
    fun presentWithPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: Configuration? = null
    ) {
        paymentSheetLauncher.presentWithPaymentIntent(paymentIntentClientSecret, configuration)
    }

    /**
     * Present the payment sheet to process a [SetupIntent].
     * If the [SetupIntent] is already confirmed, [PaymentSheetResultCallback] will be invoked
     * with [PaymentSheetResult.Completed].
     *
     * @param setupIntentClientSecret the client secret for the [SetupIntent].
     * @param configuration optional [PaymentSheet] settings.
     */
    @JvmOverloads
    fun presentWithSetupIntent(
        setupIntentClientSecret: String,
        configuration: Configuration? = null
    ) {
        paymentSheetLauncher.presentWithSetupIntent(setupIntentClientSecret, configuration)
    }

    /** Configuration for [PaymentSheet] **/
    @Parcelize
    data class Configuration @JvmOverloads constructor(
        /**
         * Your customer-facing business name.
         *
         * The default value is the name of your app.
         */
        val merchantDisplayName: String,

        /**
         * If set, the customer can select a previously saved payment method within PaymentSheet.
         */
        val customer: CustomerConfiguration? = null,

        /**
         * Configuration related to the Hyperswitch Customer making a payment.
         *
         * If set, PaymentSheet displays Google Pay as a payment option.
         */
        val googlePay: GooglePayConfiguration? = null,

        /**
         * The color of the Pay or Add button. Keep in mind the text color is white.
         *
         * If set, PaymentSheet displays the button with this color.
         */
        @Deprecated(
            message = "Use Appearance parameter to customize primary button color",
            replaceWith = ReplaceWith(
                expression = "Appearance.colorsLight/colorsDark.primary " +
                        "or PrimaryButton.colorsLight/colorsDark.background"
            )
        )
        val primaryButtonColor: ColorStateList? = null,

        /**
         * The billing information for the customer.
         *
         * If set, PaymentSheet will pre-populate the form fields with the values provided.
         */
        val defaultBillingDetails: BillingDetails? = null,

        /**
         * The shipping information for the customer.
         * If set, PaymentSheet will pre-populate the form fields with the values provided.
         * This is used to display a "Billing address is same as shipping" checkbox if `defaultBillingDetails` is not provided.
         * If `name` and `line1` are populated, it's also [attached to the PaymentIntent](https://docs.hyperswitch.io/api/payment_intents/object#payment_intent_object-shipping) during payment.
         */
        val shippingDetails: AddressDetails? = null,

        /**
         * If true, allows payment methods that do not move money at the end of the checkout.
         * Defaults to false.
         *
         * Some payment methods can't guarantee you will receive funds from your customer at the end
         * of the checkout because they take time to settle (eg. most bank debits, like SEPA or ACH)
         * or require customer action to complete (e.g. OXXO, Konbini, Boleto). If this is set to
         * true, make sure your integration listens to webhooks for notifications on whether a
         * payment has succeeded or not.
         *
         * See [payment-notification](https://docs.hyperswitch.io/payments/payment-methods#payment-notification).
         */
        val allowsDelayedPaymentMethods: Boolean = false,

        /**
         * If `true`, allows payment methods that require a shipping address, like Afterpay and
         * Affirm. Defaults to `false`.
         *
         * Set this to `true` if you collect shipping addresses via [shippingDetails] or
         * [FlowController.shippingDetails].
         *
         * **Note**: PaymentSheet considers this property `true` if `shipping` details are present
         * on the PaymentIntent when PaymentSheet loads.
         */
        val allowsPaymentMethodsRequiringShippingAddress: Boolean = false,

        /**
         * Describes the appearance of Payment Sheet.
         */
        val appearance: Appearance? = null,

        /**
         * The label to use for the primary button.
         *
         * If not set, Payment Sheet will display suitable default labels for payment and setup
         * intents.
         */
        val primaryButtonLabel: String? = null,
        val paymentSheetHeaderLabel: String? = null,
        val savedPaymentSheetHeaderLabel: String? = null,
        val displayDefaultSavedPaymentIcon: Boolean? = null,
        val displaySavedPaymentMethodsCheckbox: Boolean? = null,
        val displaySavedPaymentMethods: Boolean? = null,
        val placeHolder: PlaceHolder? = null,
        /**
         * Api key used to invoke netcetera sdk for redirection-less 3DS authentication.
         */
        val netceteraSDKApiKey: String? = null,
        val disableBranding: Boolean? = null,
        val defaultView: Boolean? = null,
        val showVersionInfo: Boolean = false
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("merchantDisplayName", merchantDisplayName)
                    putBundle("customer", customer?.bundle)
                    putBundle("googlePay", googlePay?.bundle)
                    putBundle("defaultBillingDetails", defaultBillingDetails?.bundle)
                    putBundle("shippingDetails", shippingDetails?.bundle)
                    putBoolean("allowsDelayedPaymentMethods", allowsDelayedPaymentMethods)
                    putBoolean(
                        "allowsPaymentMethodsRequiringShippingAddress",
                        allowsPaymentMethodsRequiringShippingAddress
                    )
                    putBundle("appearance", appearance?.bundle)
                    putString("primaryButtonLabel", primaryButtonLabel)
                    putString("paymentSheetHeaderLabel", paymentSheetHeaderLabel)
                    putString("savedPaymentSheetHeaderLabel", savedPaymentSheetHeaderLabel)
                    if (displayDefaultSavedPaymentIcon != null) {
                        putBoolean("displayDefaultSavedPaymentIcon", displayDefaultSavedPaymentIcon)
                    }
                    if (displaySavedPaymentMethodsCheckbox != null) {
                        putBoolean(
                            "displaySavedPaymentMethodsCheckbox",
                            displaySavedPaymentMethodsCheckbox
                        )
                    }
                    if (displaySavedPaymentMethods != null) {
                        putBoolean("displaySavedPaymentMethods", displaySavedPaymentMethods)
                    }
                    putBundle("placeHolder", placeHolder?.bundle)
                    putString("netceteraSDKApiKey", netceteraSDKApiKey)
                    if (disableBranding != null) {
                        putBoolean("disableBranding", disableBranding)
                    }
                    if (defaultView != null) {
                        putBoolean("defaultView", defaultView)
                    }
                    putBoolean("showVersionInfo", showVersionInfo)
                }
            }

        /**
         * [Configuration] builder for cleaner object creation from Java.
         */
        class Builder(
            private var merchantDisplayName: String
        ) {
            private var customer: CustomerConfiguration? = null
            private var googlePay: GooglePayConfiguration? = null
            private var primaryButtonColor: ColorStateList? = null
            private var defaultBillingDetails: BillingDetails? = null
            private var shippingDetails: AddressDetails? = null
            private var allowsDelayedPaymentMethods: Boolean = false
            private var allowsPaymentMethodsRequiringShippingAddress: Boolean = false
            private var appearance: Appearance? = null
            private var displaySavedPaymentMethodsCheckbox: Boolean = true
            private var displaySavedPaymentMethods: Boolean = true
            private var placeHolder: PlaceHolder? = null
            private var primaryButtonLabel: String? = null
            private var disableBranding: Boolean? = null
            private var defaultView: Boolean? = null
            private var displayDefaultSavedPaymentIcon: Boolean? = null
            private var paymentSheetHeaderLabel: String? = null
            private var savedPaymentSheetHeaderLabel: String? = null
            private var netceteraSDKApiKey: String? = null
            private var showVersionInfo : Boolean = false
            fun merchantDisplayName(merchantDisplayName: String) =
                apply { this.merchantDisplayName = merchantDisplayName }

            fun customer(customer: CustomerConfiguration?) =
                apply { this.customer = customer }

            fun googlePay(googlePay: GooglePayConfiguration?) =
                apply { this.googlePay = googlePay }

            @Deprecated(
                message = "Use Appearance parameter to customize primary button color",
                replaceWith = ReplaceWith(
                    expression = "Appearance.colorsLight/colorsDark.primary " +
                            "or PrimaryButton.colorsLight/colorsDark.background"
                )
            )
            fun primaryButtonColor(primaryButtonColor: ColorStateList?) =
                apply { this.primaryButtonColor = primaryButtonColor }

            fun defaultBillingDetails(defaultBillingDetails: BillingDetails?) =
                apply { this.defaultBillingDetails = defaultBillingDetails }

            fun shippingDetails(shippingDetails: AddressDetails?) =
                apply { this.shippingDetails = shippingDetails }

            fun allowsDelayedPaymentMethods(allowsDelayedPaymentMethods: Boolean) =
                apply { this.allowsDelayedPaymentMethods = allowsDelayedPaymentMethods }

            fun allowsPaymentMethodsRequiringShippingAddress(
                allowsPaymentMethodsRequiringShippingAddress: Boolean,
            ) = apply {
                this.allowsPaymentMethodsRequiringShippingAddress =
                    allowsPaymentMethodsRequiringShippingAddress
            }

            fun appearance(appearance: Appearance) =
                apply { this.appearance = appearance }

            fun displaySavedPaymentMethodsCheckbox(displaySavedPaymentMethodsCheckbox: Boolean) =
                apply {
                    this.displaySavedPaymentMethodsCheckbox = displaySavedPaymentMethodsCheckbox
                }

            fun displaySavedPaymentMethods(displaySavedPaymentMethods: Boolean) =
                apply { this.displaySavedPaymentMethods = displaySavedPaymentMethods }

            fun placeHolder(placeHolder: PlaceHolder?) =
                apply { this.placeHolder = placeHolder }

            fun primaryButtonLabel(primaryButtonLabel: String) =
                apply { this.primaryButtonLabel = primaryButtonLabel }

            fun disableBranding(disableBranding: Boolean) =
                apply { this.disableBranding = disableBranding }

            fun defaultView(defaultView: Boolean) =
                apply { this.defaultView = defaultView }

            fun displayDefaultSavedPaymentIcon(displayDefaultSavedPaymentIcon: Boolean) =
                apply { this.displayDefaultSavedPaymentIcon = displayDefaultSavedPaymentIcon }

            fun paymentSheetHeaderLabel(paymentSheetHeaderLabel: String) =
                apply { this.paymentSheetHeaderLabel = paymentSheetHeaderLabel }

            fun netceteraSDKApiKey(netceteraSDKApiKey: String?) = apply {
                this.netceteraSDKApiKey = netceteraSDKApiKey
            }

            fun showVersionInfo(showVersionInfo: Boolean) = apply {
                this.showVersionInfo = showVersionInfo
            }

            fun savedPaymentSheetHeaderLabel(savedPaymentSheetHeaderLabel: String) =
                apply { this.savedPaymentSheetHeaderLabel = savedPaymentSheetHeaderLabel }

            fun build() = Configuration(
                merchantDisplayName,
                customer,
                googlePay,
                primaryButtonColor,
                defaultBillingDetails,
                shippingDetails,
                allowsDelayedPaymentMethods,
                allowsPaymentMethodsRequiringShippingAddress,
                appearance,
                primaryButtonLabel,
                paymentSheetHeaderLabel,
                savedPaymentSheetHeaderLabel,
                displayDefaultSavedPaymentIcon,
                displaySavedPaymentMethodsCheckbox,
                displaySavedPaymentMethods,
                placeHolder,
                netceteraSDKApiKey,
                disableBranding,
                defaultView,
                showVersionInfo
            )
        }
    }

    @Parcelize
    data class Appearance(
        /**
         * Describes the colors used while the system is in light mode.
         */
        val colorsLight: Colors? = null,

        /**
         * Describes the colors used while the system is in dark mode.
         */
        val colorsDark: Colors? = null,

        /**
         * Describes the appearance of shapes.
         */
        val shapes: Shapes? = null,

        /**
         * Describes the typography used for text.
         */
        val typography: Typography? = null,

        /**
         * Describes the appearance of the primary button (e.g., the "Pay" button).
         */
        val primaryButton: PrimaryButton? = null,

        val locale: String? = null,

        val theme: Theme? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putBundle("colorsLight", colorsLight?.bundle)
                    putBundle("colorsDark", colorsDark?.bundle)
                    putBundle("shapes", shapes?.bundle)
                    putBundle("typography", typography?.bundle)
                    putBundle("primaryButton", primaryButton?.bundle)
                    putString("locale", locale)
                    putString("theme", theme?.name)
                }
            }

        class Builder {
            private var colorsLight: Colors? = null
            private var colorsDark: Colors? = null
            private var shapes: Shapes? = null
            private var typography: Typography? = null
            private var primaryButton: PrimaryButton? = null
            private var theme: Theme? = null
            private var locale: String? = null

            fun colorsLight(colors: Colors) = apply { this.colorsLight = colors }
            fun colorsDark(colors: Colors) = apply { this.colorsDark = colors }
            fun shapes(shapes: Shapes) = apply { this.shapes = shapes }
            fun typography(typography: Typography) = apply { this.typography = typography }
            fun primaryButton(primaryButton: PrimaryButton) =
                apply { this.primaryButton = primaryButton }

            fun theme(theme: Theme) = apply { this.theme = theme }
            fun locale(locale: String) = apply { this.locale = locale }
        }
    }

    @Parcelize
    data class Colors(
        /**
         * A primary color used throughout PaymentSheet.
         */
        @ColorInt
        val primary: Int? = null,

        /**
         * The color used for the surfaces (backgrounds) of PaymentSheet.
         */
        @ColorInt
        val surface: Int? = null,

        /**
         * The color used for the background of inputs, tabs, and other components.
         */
        @ColorInt
        val component: Int? = null,

        /**
         * The color used for borders of inputs, tabs, and other components.
         */
        @ColorInt
        val componentBorder: Int? = null,

        /**
         * The color of the divider lines used inside inputs, tabs, and other components.
         */
        @ColorInt
        val componentDivider: Int? = null,

        /**
         * The default color used for text and on other elements that live on components.
         */
        @ColorInt
        val onComponent: Int? = null,

        /**
         * The color used for items appearing over the background in Payment Sheet.
         */
        @ColorInt
        val onSurface: Int? = null,

        /**
         * The color used for text of secondary importance.
         * For example, this color is used for the label above input fields.
         */
        @ColorInt
        val subtitle: Int? = null,

        /**
         * The color used for input placeholder text.
         */
        @ColorInt
        val placeholderText: Int? = null,

        /**
         * The color used for icons in PaymentSheet, such as the close or back icons.
         */
        @ColorInt
        val appBarIcon: Int? = null,

        /**
         * A color used to indicate errors or destructive actions in PaymentSheet.
         */
        @ColorInt
        val error: Int? = null,

        /**
         * A color used to indicate Loader Background color in PaymentSheet.
         */
        @ColorInt
        val loaderBackground: Int? = null,

        /**
         * A color used to indicate Loader Foreground color in PaymentSheet.
         */
        @ColorInt
        val loaderForeground: Int? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("primary", toHexColorString(primary))
                    putString("surface", toHexColorString(surface))
                    putString("component", toHexColorString(component))
                    putString("componentBorder", toHexColorString(componentBorder))
                    putString("componentDivider", toHexColorString(componentDivider))
                    putString("onComponent", toHexColorString(onComponent))
                    putString("onSurface", toHexColorString(onSurface))
                    putString("subtitle", toHexColorString(subtitle))
                    putString("placeholderText", toHexColorString(placeholderText))
                    putString("appBarIcon", toHexColorString(appBarIcon))
                    putString("error", toHexColorString(error))
                    putString("loaderBackground", toHexColorString(loaderBackground))
                    putString("loaderForeground", toHexColorString(loaderForeground))
                }
            }

        private fun toHexColorString(color: Int?): String? {
            if (color == null) return null
            val s = String.format("#%08X", (color))
            return "#" + s.substring(3) + s.substring(1, 3)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        constructor(
            primary: Color? = null,
            surface: Color? = null,
            component: Color? = null,
            componentBorder: Color? = null,
            componentDivider: Color? = null,
            onComponent: Color? = null,
            subtitle: Color? = null,
            placeholderText: Color? = null,
            onSurface: Color? = null,
            appBarIcon: Color? = null,
            error: Color? = null,
            loaderBackground: Color? = null,
            loaderForeground: Color? = null,
        ) : this(
            primary = primary?.toArgb(),
            surface = surface?.toArgb(),
            component = component?.toArgb(),
            componentBorder = componentBorder?.toArgb(),
            componentDivider = componentDivider?.toArgb(),
            onComponent = onComponent?.toArgb(),
            subtitle = subtitle?.toArgb(),
            placeholderText = placeholderText?.toArgb(),
            onSurface = onSurface?.toArgb(),
            appBarIcon = appBarIcon?.toArgb(),
            error = error?.toArgb(),
            loaderBackground = loaderBackground?.toArgb(),
            loaderForeground = loaderForeground?.toArgb(),
        )
    }

    @Parcelize
    data class Shadow(
        /**
         * The color used for setting Shadow color.
         */
        val color: Int?,

        /**
         * The intensity used for setting intensity of Shadow in PaymentSheet.
         */
        val intensity: Float?
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("color", toHexColorString(color))
                    if (intensity != null) {
                        putFloat("intensity", intensity)
                    }
                }
            }

        private fun toHexColorString(color: Int?): String? {
            if (color == null) return null
            val s = String.format("#%08X", (color))
            return "#" + s.substring(3) + s.substring(1, 3)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        constructor(
            color: Color?,
            intensity: Float?
        ) : this(
            color = color?.toArgb(),
            intensity = intensity
        )
    }

    @Parcelize
    data class Shapes(
        /**
         * The corner radius used for tabs, inputs, buttons, and other components in PaymentSheet.
         */
        val cornerRadiusDp: Float?,

        /**
         * The border used for inputs, tabs, and other components in PaymentSheet.
         */
        val borderStrokeWidthDp: Float?,

        /**
         * The shadow used for inputs, tabs, and other components in PaymentSheet.
         */
        val shadow: Shadow?
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    if (cornerRadiusDp != null) {
                        putFloat("cornerRadiusDp", cornerRadiusDp)
                    }
                    if (borderStrokeWidthDp != null) {
                        putFloat("borderStrokeWidthDp", borderStrokeWidthDp)
                    }
                    putBundle("shadow", shadow?.bundle)
                }
            }
    }

    @Parcelize
    data class Typography(
        /**
         * The scale factor for all fonts in PaymentSheet, the default value is 1.0.
         * When this value increases fonts will increase in size and decrease when this value is lowered.
         */
        val sizeScaleFactor: Float? = null,

        /**
         * The font used in text. This should be a resource ID value.
         */
        @FontRes
        val fontResId: Int? = null,

        /**
         * The font used for lite SDK.
         */
        val fontFamily: String? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    if (sizeScaleFactor != null) {
                        putFloat("sizeScaleFactor", sizeScaleFactor)
                    }
                    if (fontResId != null) {
                        putInt("fontResId", fontResId)
                    }
                    if (fontFamily != null) {
                        putString("family", fontFamily)
                    }
                }
            }
    }

    @Parcelize
    data class PrimaryButton(
        /**
         * Describes the colors used while the system is in light mode.
         */
        val colorsLight: PrimaryButtonColors? = null,
        /**
         * Describes the colors used while the system is in dark mode.
         */
        val colorsDark: PrimaryButtonColors? = null,
        /**
         * Describes the shape of the primary button.
         */
        val shape: PrimaryButtonShape? = null,
        /**
         * Describes the typography of the primary button.
         */
        val typography: PrimaryButtonTypography? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putBundle("colorsLight", colorsLight?.bundle)
                    putBundle("colorsDark", colorsDark?.bundle)
                    putBundle("shape", shape?.bundle)
                    putBundle("typography", typography?.bundle)
                }
            }
    }

    @Parcelize
    data class PrimaryButtonColors(
        /**
         * The background color of the primary button.
         * Note: If 'null', {@link Colors#primary} is used.
         */
        @ColorInt
        val background: Int?,
        /**
         * The color of the text and icon in the primary button.
         */
        @ColorInt
        val onBackground: Int?,
        /**
         * The border color of the primary button.
         */
        @ColorInt
        val border: Int?
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    if (background != null) {
                        putInt("background", background)
                    }
                    if (onBackground != null) {
                        putInt("onBackground", onBackground)
                    }
                    if (border != null) {
                        putInt("border", border)
                    }
                }
            }

        @RequiresApi(Build.VERSION_CODES.O)
        constructor(
            background: Color?,
            onBackground: Color?,
            border: Color?
        ) : this(
            background = background?.toArgb(),
            onBackground = onBackground?.toArgb(),
            border = border?.toArgb()
        )
    }

    @Parcelize
    data class PrimaryButtonShape(
        /**
         * The corner radius of the primary button.
         * Note: If 'null', {@link Shapes#cornerRadiusDp} is used.
         */
        val cornerRadiusDp: Float? = null,
        /**
         * The border width of the primary button.
         * Note: If 'null', {@link Shapes#borderStrokeWidthDp} is used.
         */
        val borderStrokeWidthDp: Float? = null,
        /**
         * The shadow of the primary button.
         * Note: If 'null', {@link Shapes#borderStrokeWidthDp} is used.
         */
        val shadow: Shadow? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    if (cornerRadiusDp != null) {
                        putFloat("cornerRadiusDp", cornerRadiusDp)
                    }
                    if (borderStrokeWidthDp != null) {
                        putFloat("borderStrokeWidthDp", borderStrokeWidthDp)
                    }
                    putBundle("shadow", shadow?.bundle)
                }
            }
    }

    @Parcelize
    data class PrimaryButtonTypography(
        /**
         * The font used in the primary button.
         * Note: If 'null', Appearance.Typography.fontResId is used.
         */
        @FontRes
        val fontResId: Int? = null,

        /**
         * The font size in the primary button.
         * Note: If 'null', {@link Typography#sizeScaleFactor} is used.
         */
        val fontSizeSp: Float? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    if (fontResId != null) {
                        putInt("fontResId", fontResId)
                    }
                    if (fontSizeSp != null) {
                        putFloat("fontSizeSp", fontSizeSp)
                    }
                }
            }
    }

    @Parcelize
    data class Address(
        /**
         * City, district, suburb, town, or village.
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val city: String? = null,
        /**
         * Two-letter country code (ISO 3166-1 alpha-2).
         */
        val country: String? = null,
        /**
         * Address line 1 (e.g., street, PO Box, or company name).
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val line1: String? = null,
        /**
         * Address line 2 (e.g., apartment, suite, unit, or building).
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val line2: String? = null,
        /**
         * ZIP or postal code.
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val postalCode: String? = null,
        /**
         * State, county, province, or region.
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val state: String? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("city", city)
                    putString("country", country)
                    putString("line1", line1)
                    putString("line2", line2)
                    putString("postalCode", postalCode)
                    putString("state", state)
                }
            }

        /**
         * [Address] builder for cleaner object creation from Java.
         */
        class Builder {
            private var city: String? = null
            private var country: String? = null
            private var line1: String? = null
            private var line2: String? = null
            private var postalCode: String? = null
            private var state: String? = null

            fun city(city: String?) = apply { this.city = city }
            fun country(country: String?) = apply { this.country = country }
            fun line1(line1: String?) = apply { this.line1 = line1 }
            fun line2(line2: String?) = apply { this.line2 = line2 }
            fun postalCode(postalCode: String?) = apply { this.postalCode = postalCode }
            fun state(state: String?) = apply { this.state = state }

            fun build() = Address(city, country, line1, line2, postalCode, state)
        }
    }

    @Parcelize
    data class BillingDetails(
        /**
         * The customer's billing address.
         */
        val address: Address? = null,
        /**
         * The customer's email.
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val email: String? = null,
        /**
         * The customer's full name.
         * The value set is displayed in the payment sheet as-is. Depending on the payment method, the customer may be required to edit this value.
         */
        val name: String? = null,
        /**
         * The customer's phone number without formatting e.g. 5551234567
         */
        val phone: String? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putBundle("address", address?.bundle) // Assuming Address implements Parcelable
                    putString("email", email)
                    putString("name", name)
                    putString("phone", phone)
                }
            }

        /**
         * [BillingDetails] builder for cleaner object creation from Java.
         */
        class Builder {
            private var address: Address? = null
            private var email: String? = null
            private var name: String? = null
            private var phone: String? = null

            fun address(address: Address?) = apply { this.address = address }
            fun address(addressBuilder: Address.Builder) =
                apply { this.address = addressBuilder.build() }

            fun email(email: String?) = apply { this.email = email }
            fun name(name: String?) = apply { this.name = name }
            fun phone(phone: String?) = apply { this.phone = phone }

            fun build() = BillingDetails(address, email, name, phone)
        }
    }

    @Parcelize
    data class CustomerConfiguration(
        /**
         * The identifier of the Hyperswitch Customer object.
         * See [Hyperswitch's documentation](https://docs.hyperswitch.io/api/customers/object#customer_object-id).
         */
        val id: String?,

        /**
         * A short-lived token that allows the SDK to access a Customer's payment methods.
         */
        val ephemeralKeySecret: String?
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("id", id)
                    putString("ephemeralKeySecret", ephemeralKeySecret)
                }
            }
    }

    @Parcelize
    data class GooglePayConfiguration(
        /**
         * The Google Pay environment to use.
         *
         * See [Google's documentation](https://developers.google.com/android/reference/com/google/android/gms/wallet/Wallet.WalletOptions#environment) for more information.
         */
        val environment: Environment,
        /**
         * The two-letter ISO 3166 code of the country of your business, e.g. "US".
         * See your account's country value [here](https://app.hyperswitch.io/settings/account).
         */
        val countryCode: String,
        /**
         * The three-letter ISO 4217 alphabetic currency code, e.g. "USD" or "EUR".
         * Required in order to support Google Pay when processing a Setup Intent.
         */
        val currencyCode: String? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("environment", environment.name)
                    putString("countryCode", countryCode)
                    putString("currencyCode", currencyCode)
                }
            }

        constructor(
            environment: Environment,
            countryCode: String
        ) : this(environment, countryCode, null)

        enum class Environment {
            Production,
            Test
        }
    }

    @Parcelize
    data class PlaceHolder(
        val cardNumber: String? = null,
        val expiryDate: String? = null,
        val cvv: String? = null
    ) : Parcelable {
        val bundle: Bundle
            get() {
                return Bundle().apply {
                    putString("cardNumber", cardNumber)
                    putString("expiryDate", expiryDate)
                    putString("cvv", cvv)
                }
            }
    }

    enum class Theme {
        Light,
        Dark,
        FlatMinimal,
        Minimal,
        Default,
    }

    /**
     * A class that presents the individual steps of a payment sheet flow.
     */
    interface FlowController {

        var shippingDetails: AddressDetails?

        /**
         * Configure the FlowController to process a [PaymentIntent].
         *
         * @param paymentIntentClientSecret the client secret for the [PaymentIntent].
         * @param configuration optional [PaymentSheet] settings.
         * @param callback called with the result of configuring the FlowController.
         */
        fun configureWithPaymentIntent(
            paymentIntentClientSecret: String,
            configuration: Configuration? = null,
            callback: ConfigCallback
        )

        /**
         * Configure the FlowController to process a [SetupIntent].
         *
         * @param setupIntentClientSecret the client secret for the [SetupIntent].
         * @param configuration optional [PaymentSheet] settings.
         * @param callback called with the result of configuring the FlowController.
         */
        fun configureWithSetupIntent(
            setupIntentClientSecret: String,
            configuration: Configuration? = null,
            callback: ConfigCallback
        )

        /**
         * Retrieve information about the customer's desired payment option.
         * You can use this to e.g. display the payment option in your UI.
         */
        fun getPaymentOption(): PaymentOption?

        /**
         * Present a sheet where the customer chooses how to pay, either by selecting an existing
         * payment method or adding a new one.
         * Call this when your "Select a payment method" button is tapped.
         */
        fun presentPaymentOptions()

        /**
         * Complete the payment or setup.
         */
        fun confirm()

        sealed class Result {
            object Success : Result()

            class Failure(
                val error: Throwable
            ) : Result()
        }

        fun interface ConfigCallback {
            fun onConfigured(
                success: Boolean,
                error: Throwable?
            )
        }

        companion object {

            /**
             * Create the FlowController when launching the payment sheet from an Activity.
             *
             * @param activity  the Activity that is presenting the payment sheet.
             * @param paymentOptionCallback called when the customer's desired payment method
             *      changes.  Called in response to the [PaymentSheet#presentPaymentOptions()]
             * @param paymentResultCallback called when a [PaymentSheetResult] is available.
             */
            @JvmStatic
            fun create(
                activity: Activity,
                paymentOptionCallback: PaymentOptionCallback,
                paymentResultCallback: PaymentSheetResultCallback
            ): FlowController {
                return FlowControllerFactory(
                    activity,
                    paymentOptionCallback,
                    paymentResultCallback
                ).create()
            }

            /**
             * Create the FlowController when launching the payment sheet from a Fragment.
             *
             * @param fragment the Fragment that is presenting the payment sheet.
             * @param paymentOptionCallback called when the customer's [PaymentOption] selection changes.
             * @param paymentResultCallback called when a [PaymentSheetResult] is available.
             */
            @JvmStatic
            fun create(
                fragment: Fragment,
                paymentOptionCallback: PaymentOptionCallback,
                paymentResultCallback: PaymentSheetResultCallback
            ): FlowController {
                return FlowControllerFactory(
                    fragment,
                    paymentOptionCallback,
                    paymentResultCallback
                ).create()
            }
        }
    }
}