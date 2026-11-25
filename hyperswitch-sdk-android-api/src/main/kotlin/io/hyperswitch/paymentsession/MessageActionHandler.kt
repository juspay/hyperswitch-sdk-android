package io.hyperswitch.paymentsession

/** A tapped payment method message action, as client-core reports it. */
typealias MessageActionHandler =
    (paymentMethod: String, paymentMethodType: String, index: Int, text: String) -> Unit
