package io.hyperswitch.paymentsession

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Configuration for filtering saved payment methods returned by
 * [PaymentSessionLauncher.getCustomerSavedPaymentMethods].
 *
 * @param hiddenPaymentMethods Payment method types to exclude from the saved methods list
 *   (e.g. `listOf("google_pay", "paypal")`). Matches against the `payment_method_type` field
 *   returned by the Hyperswitch API.
 */
@Parcelize
data class SavedPaymentMethodsConfiguration(
    val hiddenPaymentMethods: List<String> = emptyList(),
) : Parcelable {

    val bundle: Bundle
        get() = Bundle().apply {
            putBundle(
                "paymentMethodLayout", Bundle().apply {
                    putBundle(
                        "savedMethodCustomization", Bundle().apply {
                            putStringArrayList(
                                "hiddenPaymentMethods",
                                ArrayList(hiddenPaymentMethods)
                            )
                        }
                    )
                }
            )
        }
}
