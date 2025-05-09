package io.hyperswitch.testapp

import io.hyperswitch.paymentsheet.PaymentSheet

import android.graphics.Color
import io.hyperswitch.paymentsheet.AddressDetails
import io.hyperswitch.paymentsheet.PaymentSheet.Colors
import io.hyperswitch.paymentsheet.PaymentSheet.PrimaryButton
import io.hyperswitch.paymentsheet.PaymentSheet.Shapes
import io.hyperswitch.paymentsheet.PaymentSheet.Theme
import io.hyperswitch.paymentsheet.PaymentSheet.Typography
import org.json.JSONObject
import java.util.Locale

/**
 * Converts a JSON string into a PaymentSheet.Configuration object
 */
object JsonToConfigurationConverter {

    /**
     * Parse JSON string and create a PaymentSheet.Configuration
     * @param jsonString JSON representation of PaymentSheet configuration
     * @return PaymentSheet.Configuration object
     */
    fun createConfigurationFromJson(jsonString: String): PaymentSheet.Configuration {
        val json = JSONObject(jsonString)

        // Create configuration builder with required merchantDisplayName
        val builder = PaymentSheet.Configuration.Builder(
            json.optString("merchantDisplayName")
        )

        // Customer Configuration
        if (json.has("customer")) {
            val customerJson = json.getJSONObject("customer")
            if (customerJson.has("id") || customerJson.has("ephemeralKeySecret")) {
                builder.customer(
                    PaymentSheet.CustomerConfiguration(
                        customerJson.optString("id"),
                        customerJson.optString("ephemeralKeySecret")
                    )
                )
            }
        }

        // Google Pay Configuration
        if (json.has("googlePay")) {
            val googlePayJson = json.getJSONObject("googlePay")
            val environment = PaymentSheet.GooglePayConfiguration.Environment.valueOf(
                googlePayJson.optString("environment")
            )

            val countryCode = googlePayJson.getString("countryCode")

            if (googlePayJson.has("currencyCode")) {
                builder.googlePay(
                    PaymentSheet.GooglePayConfiguration(
                        environment,
                        countryCode,
                        googlePayJson.getString("currencyCode")
                    )
                )
            } else {
                builder.googlePay(
                    PaymentSheet.GooglePayConfiguration(
                        environment,
                        countryCode
                    )
                )
            }
        }

        //Primary button Color


        // Default Billing Details
        if (json.has("defaultBillingDetails")) {
            builder.defaultBillingDetails(parseBillingDetails(json.getJSONObject("defaultBillingDetails")))
        }

        // Shipping Details
        if (json.has("shippingDetails")) {
            builder.shippingDetails(parseAddressDetails(json.getJSONObject("shippingDetails")))
        }

        // Delayed Payment Methods
        if (json.has("allowsDelayedPaymentMethods")) {
            builder.allowsDelayedPaymentMethods(json.getBoolean("allowsDelayedPaymentMethods"))
        }

        // Payment Methods Requiring Shipping Address
        if (json.has("allowsPaymentMethodsRequiringShippingAddress")) {
            builder.allowsPaymentMethodsRequiringShippingAddress(
                json.getBoolean("allowsPaymentMethodsRequiringShippingAddress")
            )
        }

        // Appearance
        if (json.has("appearance")) {
            builder.appearance(parseAppearance(json.getJSONObject("appearance")))
        }

        // Primary Button Label
        if (json.has("primaryButtonLabel")) {
            builder.primaryButtonLabel(json.getString("primaryButtonLabel"))
        }

        // Payment Sheet Header Label
        if (json.has("paymentSheetHeaderLabel")) {
            builder.paymentSheetHeaderLabel(json.getString("paymentSheetHeaderLabel"))
        }

        // Saved Payment Sheet Header Label
        if (json.has("savedPaymentSheetHeaderLabel")) {
            builder.savedPaymentSheetHeaderLabel(json.getString("savedPaymentSheetHeaderLabel"))
        }

        // Display Default Saved Payment Icon
        if (json.has("displayDefaultSavedPaymentIcon")) {
            builder.displayDefaultSavedPaymentIcon(json.getBoolean("displayDefaultSavedPaymentIcon"))
        }

        // Display Saved Payment Methods Checkbox
        if (json.has("displaySavedPaymentMethodsCheckbox")) {
            builder.displaySavedPaymentMethodsCheckbox(json.getBoolean("displaySavedPaymentMethodsCheckbox"))
        }

        // Display Saved Payment Methods
        if (json.has("displaySavedPaymentMethods")) {
            builder.displaySavedPaymentMethods(json.getBoolean("displaySavedPaymentMethods"))
        }

        // Placeholder
        if (json.has("placeHolder")) {
            val placeHolderJson = json.getJSONObject("placeHolder")
            builder.placeHolder(
                PaymentSheet.PlaceHolder(
                    cardNumber = getOptionalString(placeHolderJson, "cardNumber"),
                    expiryDate = getOptionalString(placeHolderJson, "expiryDate"),
                    cvv = getOptionalString(placeHolderJson, "cvv")
                )
            )
        }

        // Netcetera SDK API Key
        if (json.has("netceteraSDKApiKey")) {
            builder.netceteraSDKApiKey(json.getString("netceteraSDKApiKey"))
        }

        // Disable Branding
        if (json.has("disableBranding")) {
            builder.disableBranding(json.getBoolean("disableBranding"))
        }

        // Default View
        if (json.has("defaultView")) {
            builder.defaultView(json.getBoolean("defaultView"))
        }

        // Show Version Info
        if (json.has("showVersionInfo")) {
            builder.showVersionInfo(json.getBoolean("showVersionInfo"))
        }

        return builder.build()
    }

    private fun parseBillingDetails(json: JSONObject): PaymentSheet.BillingDetails {
        val builder = PaymentSheet.BillingDetails.Builder()

        if (json.has("address")) {
            builder.address(parseAddress(json.getJSONObject("address")))
        }

        getOptionalString(json, "email")?.let { builder.email(it) }
        getOptionalString(json, "name")?.let { builder.name(it) }
        getOptionalString(json, "phone")?.let { builder.phone(it) }

        return builder.build()
    }

    private fun parseAddressDetails(json: JSONObject): AddressDetails {
        return AddressDetails(
            name = getOptionalString(json, "name"),
            address = parseAddress(json.getJSONObject("address")),
            phoneNumber = getOptionalString(json, "phoneNumber"),

            isCheckboxSelected = getOptionalString(json, "phoneNumber")?.let {
                true
            }
        )
    }

    private fun parseAddress(json: JSONObject): PaymentSheet.Address {
        val builder = PaymentSheet.Address.Builder()

        getOptionalString(json, "city")?.let { builder.city(it) }
        getOptionalString(json, "country")?.let { builder.country(it) }
        getOptionalString(json, "line1")?.let { builder.line1(it) }
        getOptionalString(json, "line2")?.let { builder.line2(it) }
        getOptionalString(json, "postalCode")?.let { builder.postalCode(it) }
        getOptionalString(json, "state")?.let { builder.state(it) }

        return builder.build()
    }

    private fun parseAppearance(json: JSONObject): PaymentSheet.Appearance {
        var colorsLight: Colors? = null
        var colorsDark: Colors? = null
        var shapes: Shapes? = null
        var typography: Typography? = null
        var primaryButton: PrimaryButton? = null
        var locale: String? = null
        var theme: Theme? = null
        if (json.has("typography")) {
            typography = parseTypography(json.getJSONObject("typography"))
        }
        if (json.has("colorsLight")) {
            colorsLight = parseColors(json.getJSONObject("colorsLight"))
        }
        if (json.has("colorsDark")) {
            colorsDark = parseColors(json.getJSONObject("colorsDark"))
        }
        if (json.has("shapes")) {
            shapes = parseShapes(json.getJSONObject("shapes"))
        }
        if (json.has("primaryButton")) {
            primaryButton = parsePrimaryButton(json.getJSONObject("primaryButton"))
        }

        if (json.has("locale")) {
            locale = json.getString("locale")
        }
        if (json.has("theme")) {
            theme = PaymentSheet.Theme.valueOf(json.getString("theme")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
        }

        return PaymentSheet.Appearance(
            colorsLight,
            colorsDark,
            shapes,
            typography,
            primaryButton,
            locale,
            theme
        )
    }

    private fun parseColors(json: JSONObject): PaymentSheet.Colors {
        return PaymentSheet.Colors(
            primary = parseColor(getOptionalString(json, "primary")),
            surface = parseColor(getOptionalString(json, "surface")),
            component = parseColor(getOptionalString(json, "component")),
            componentBorder = parseColor(getOptionalString(json, "componentBorder")),
            componentDivider = parseColor(getOptionalString(json, "componentDivider")),
            onComponent = parseColor(getOptionalString(json, "onComponent")),
            onSurface = parseColor(getOptionalString(json, "onSurface")),
            subtitle = parseColor(getOptionalString(json, "subtitle")),
            placeholderText = parseColor(getOptionalString(json, "placeholderText")),
            appBarIcon = parseColor(getOptionalString(json, "appBarIcon")),
            error = parseColor(getOptionalString(json, "error")),
            loaderBackground = parseColor(getOptionalString(json, "loaderBackground")),
            loaderForeground = parseColor(getOptionalString(json, "loaderForeground"))
        )
    }

    private fun parseShapes(json: JSONObject): PaymentSheet.Shapes {
        val cornerRadiusDp =
            if (json.has("cornerRadiusDp")) json.getDouble("cornerRadiusDp").toFloat() else null
        val borderStrokeWidthDp =
            if (json.has("borderStrokeWidthDp")) json.getDouble("borderStrokeWidthDp")
                .toFloat() else null
        val shadow = if (json.has("shadow")) parseShadow(json.getJSONObject("shadow")) else null

        return PaymentSheet.Shapes(
            cornerRadiusDp = cornerRadiusDp,
            borderStrokeWidthDp = borderStrokeWidthDp,
            shadow = shadow
        )
    }

    private fun parseShadow(json: JSONObject): PaymentSheet.Shadow {
        val colorString = getOptionalString(json, "color")
        val intensity = if (json.has("intensity")) json.getDouble("intensity").toFloat() else null

        return PaymentSheet.Shadow(
            color = parseColor(colorString),
            intensity = intensity
        )
    }

    private fun parseTypography(json: JSONObject): PaymentSheet.Typography {
        val sizeScaleFactor =
            if (json.has("sizeScaleFactor")) json.getDouble("sizeScaleFactor").toFloat() else null
        val fontResId = if (json.has("fontResId")) json.getInt("fontResId") else null

        return PaymentSheet.Typography(
            sizeScaleFactor = sizeScaleFactor,
            fontResId = fontResId
        )
    }

    private fun parsePrimaryButton(json: JSONObject): PaymentSheet.PrimaryButton {
        val colorsLight = if (json.has("colorsLight")) {
            parsePrimaryButtonColors(json.getJSONObject("colorsLight"))
        } else null

        val colorsDark = if (json.has("colorsDark")) {
            parsePrimaryButtonColors(json.getJSONObject("colorsDark"))
        } else null

        val shape = if (json.has("shape")) {
            parsePrimaryButtonShape(json.getJSONObject("shape"))
        } else null

        val typography = if (json.has("typography")) {
            parsePrimaryButtonTypography(json.getJSONObject("typography"))
        } else null

        return PaymentSheet.PrimaryButton(
            colorsLight = colorsLight,
            colorsDark = colorsDark,
            shape = shape,
            typography = typography
        )
    }

    private fun parsePrimaryButtonColors(json: JSONObject): PaymentSheet.PrimaryButtonColors {
        return PaymentSheet.PrimaryButtonColors(
            background = parseColor(getOptionalString(json, "background")),
            onBackground = parseColor(getOptionalString(json, "onBackground")),
            border = parseColor(getOptionalString(json, "border"))
        )
    }

    private fun parsePrimaryButtonShape(json: JSONObject): PaymentSheet.PrimaryButtonShape {
        val cornerRadiusDp =
            if (json.has("cornerRadiusDp")) json.getDouble("cornerRadiusDp").toFloat() else null
        val borderStrokeWidthDp =
            if (json.has("borderStrokeWidthDp")) json.getDouble("borderStrokeWidthDp")
                .toFloat() else null
        val shadow = if (json.has("shadow")) parseShadow(json.getJSONObject("shadow")) else null

        return PaymentSheet.PrimaryButtonShape(
            cornerRadiusDp = cornerRadiusDp,
            borderStrokeWidthDp = borderStrokeWidthDp,
            shadow = shadow
        )
    }

    private fun parsePrimaryButtonTypography(json: JSONObject): PaymentSheet.PrimaryButtonTypography {
        val fontResId = if (json.has("fontResId")) json.getInt("fontResId") else null
        val fontSizeSp =
            if (json.has("fontSizeSp")) json.getDouble("fontSizeSp").toFloat() else null

        return PaymentSheet.PrimaryButtonTypography(
            fontResId = fontResId,
            fontSizeSp = fontSizeSp
        )
    }

    // Helper functions
    private fun getOptionalString(json: JSONObject, key: String): String? {
        return if (json.has(key) && !json.isNull(key)) json.getString(key) else null
    }

    private fun parseColor(colorString: String?): Int? {
        if (colorString == null) return null

        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            null
        }
    }
}