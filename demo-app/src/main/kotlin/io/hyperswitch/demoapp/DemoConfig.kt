package io.hyperswitch.demoapp

import androidx.core.graphics.toColorInt
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet

// ── Appearance ─────────────────────────────────────────────────────────────────────────────────

fun buildAppearance(): PaymentSheet.Appearance = PaymentSheet.Appearance(
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

// ── Wallet configuration ────────────────────────────────────────────────────────────────────────

fun buildWallets(): PaymentSheet.WalletConfiguration = PaymentSheet.WalletConfiguration(
    googlePay = PaymentSheet.GooglePayWalletConfig(
        visibility       = PaymentSheet.WalletShowType.Auto,
        buttonType       = PaymentSheet.GooglePayButtonType.BUY,
        buttonStyleLight = PaymentSheet.GooglePayButtonStyle.Dark,
        buttonStyleDark  = PaymentSheet.GooglePayButtonStyle.Dark,
    ),
)

// ── Placeholder ────────────────────────────────────────────────────────────────────────────────

fun buildPlaceHolder(): PaymentSheet.PlaceHolder = PaymentSheet.PlaceHolder(
    cardNumber = "4242 4242 4242 4242",
    expiryDate = "MM / YY",
    cvv        = "CVC",
)

// ── Address ────────────────────────────────────────────────────────────────────────────────────

fun buildAddress(): PaymentSheet.Address = PaymentSheet.Address.Builder()
    .city("San Francisco")
    .country("US")
    .line1("123 Main St")
    .line2("Apt 4B")
    .postalCode("94102")
    .state("CA")
    .build()

// ── Billing & shipping ─────────────────────────────────────────────────────────────────────────

fun buildBillingDetails(): PaymentSheet.BillingDetails = PaymentSheet.BillingDetails.Builder()
    .address(buildAddress())
    .email("john@example.com")
    .name("John Doe")
    .phone("+919999999999")
    .build()

fun buildShippingDetails(): AddressDetails = AddressDetails(
    name               = "John Doe",
    address            = buildAddress(),
    phoneNumber        = "+919999999999",
    isCheckboxSelected = true,
)

// ── Customer ───────────────────────────────────────────────────────────────────────────────────

fun buildCustomer(): PaymentSheet.CustomerConfiguration = PaymentSheet.CustomerConfiguration(
    id                 = "cus_xxxxxxxxxxxx",
    ephemeralKeySecret = "ephem_xxxxxxxxxxxx",
)

// ── Payment method layout ──────────────────────────────────────────────────────────────────────

fun buildPaymentMethodLayout(): PaymentSheet.PaymentMethodLayout = PaymentSheet.PaymentMethodLayout(
    type               = PaymentSheet.LayoutType.Tabs,
    radios             = false,
    maxAccordionItems  = 3,
    spacedAccordionItems = true,
    defaultCollapsed   = true,
    savedMethodCustomization = PaymentSheet.SavedMethodCustomization(
        defaultCollapsed = false,
        hideCardExpiry   = true,
        hideCVCError     = false,
        cvcIcon          = PaymentSheet.WalletShowType.Auto,
        groupingBehavior = PaymentSheet.GroupingBehavior(
            displayInSeparateScreen = true,
            groupByPaymentMethods   = false,
        ),
    ),
)

// ── Full configuration ─────────────────────────────────────────────────────────────────────────

/**
 * Builds the demo [PaymentSheet.Configuration] that matches the web DemoApp reference
 * configuration in `DemoAppIndex.js`.
 *
 * @param netceteraApiKey optional Netcetera 3DS SDK key fetched from the backend.
 */
fun buildDemoConfiguration(netceteraApiKey: String? = null): PaymentSheet.Configuration =
    PaymentSheet.Configuration.Builder("Example, Inc.")
        .appearance(buildAppearance())
        .wallets(buildWallets())
        .placeHolder(buildPlaceHolder())
        .defaultBillingDetails(buildBillingDetails())
        .shippingDetails(buildShippingDetails())
        .customer(buildCustomer())
        .primaryButtonLabel("Pay Now")
        .paymentSheetHeaderLabel("Select a payment method")
        .savedPaymentSheetHeaderLabel("Saved payment method")
        .allowsDelayedPaymentMethods(false)
        .allowsPaymentMethodsRequiringShippingAddress(false)
        .displaySavedPaymentMethodsCheckbox(true)
        .displaySavedPaymentMethods(true)
        .displayDefaultSavedPaymentIcon(true)
        .disableBranding(true)
        .stickyPayButton(true)
        .redirectionInfo(PaymentSheet.WalletShowType.Never)
        .paymentMethodOrder(
            listOf("apple_pay", "google_pay", "paypal", "samsung_pay", "credit", "klarna")
        )
        .paymentMethodsConfig(
            listOf(
                PaymentSheet.PaymentMethodConfig(paymentMethod = "card",   message = ""),
                PaymentSheet.PaymentMethodConfig(paymentMethod = "wallet", message = ""),
            )
        )
        .paymentMethodLayout(buildPaymentMethodLayout())
        .showVersionInfo(true)
        .also { builder -> netceteraApiKey?.let { builder.netceteraSDKApiKey(it) } }
        .build()
