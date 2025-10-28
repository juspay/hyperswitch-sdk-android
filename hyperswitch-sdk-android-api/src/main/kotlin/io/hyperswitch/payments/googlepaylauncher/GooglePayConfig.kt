package io.hyperswitch.payments.googlepaylauncher

import android.os.Parcelable
import io.hyperswitch.payments.GooglePayEnvironment
import kotlinx.parcelize.Parcelize

// Assuming GooglePayEnvironment is accessible from its original location or moved appropriately.
// It's typically defined in the same package or within GooglePayPaymentMethodLauncher.kt

@Parcelize
data class Config(
    val googlePayConfig: GooglePayPaymentMethodLauncher.Config
) : Parcelable {
    constructor(
        environment: GooglePayEnvironment,
        merchantCountryCode: String,
        merchantName: String
    ) : this(
        GooglePayPaymentMethodLauncher.Config(
            environment,
            merchantCountryCode,
            merchantName,
            false,
            GooglePayPaymentMethodLauncher.BillingAddressConfig()
        )
    )
} 