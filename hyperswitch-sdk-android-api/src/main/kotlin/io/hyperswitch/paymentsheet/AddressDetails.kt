package io.hyperswitch.paymentsheet

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddressDetails(
    /**
     * The customer's full name
     */
    val name: String? = null,

    /**
     * The customer's address
     */
    val address: PaymentSheet.Address? = null,

    /**
     * The customer's phone number, without formatting e.g. "5551234567"
     */
    val phoneNumber: String? = null,

    /**
     * Whether or not your custom checkbox is selected.
     * Note: The checkbox is displayed below the other fields when AdditionalFieldsConfiguration.checkboxLabel is set.
     */
    val isCheckboxSelected: Boolean? = null
) : Parcelable {
    val bundle: Bundle
        get() {
            return Bundle().apply {
                putString("name", name)
                putBundle("address", address?.bundle)
                putString("phoneNumber", phoneNumber)
                if (isCheckboxSelected != null) {
                    putBoolean("isCheckboxSelected", isCheckboxSelected)
                }
            }
        }

    companion object {
        const val KEY = "AddressDetails"
    }
}

internal fun AddressLauncher.DefaultAddressDetails.toAddressDetails(): AddressDetails =
    AddressDetails(
        name = this.name,
        address = this.address,
        phoneNumber = this.phoneNumber,
        isCheckboxSelected = this.isCheckboxSelected
    )
