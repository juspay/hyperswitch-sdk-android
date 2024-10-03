package io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet

import io.hyperswitch.hyperswitch_android_sdk_web.paymentsheet.AddressLauncherResult

/**
 * Callback that is invoked when a [AddressLauncherResult] is available.
 */
internal fun interface AddressLauncherResultCallback {
    fun onAddressLauncherResult(addressLauncherResult: AddressLauncherResult)
}
