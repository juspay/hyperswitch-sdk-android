package io.hyperswitch.paymentmethods

import io.hyperswitch.sdk.HyperswitchInstance

/**
 * Payment-method session entry point.
 *
 * ```kotlin
 * val hyperswitchInstance = Hyperswitch.init(activity, config)
 * val pmsInstance = hyperswitchInstance.initPaymentMethodSession("sdk_auth", configObject)
 * ```
 *
 * Every returned [PaymentMethodSession] owns a **separate React Native host** — sessions
 * are fully isolated from each other and from the main payment SDK's host.
 */

/**
 * Creates a payment-method session for the given `sdkAuthorization`.
 *
 * @param sdkAuthorization session authorisation token issued by the merchant backend.
 * @param configuration session config object (`vault_type`, `vault_data`, extra props).
 */
fun HyperswitchInstance.initPaymentMethodSession(
    sdkAuthorization: String,
    configuration: PaymentMethodSessionConfiguration,
): PaymentMethodSession = PaymentMethodSession(
    activity = getActivity(),
    sdkAuthorization = sdkAuthorization,
    configuration = configuration,
    configurationDeferred = configurationDeferred,
)

/** Creates a payment-method session with a default (empty) configuration. */
fun HyperswitchInstance.initPaymentMethodSession(
    sdkAuthorization: String,
): PaymentMethodSession = initPaymentMethodSession(
    sdkAuthorization,
    PaymentMethodSessionConfiguration(),
)
