package io.hyperswitch.paymentsheet

internal interface PaymentSheetLauncher {
    fun presentWithPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null
    )

    fun presentWithSetupIntent(
        setupIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null
    )
}