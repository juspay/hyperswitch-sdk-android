package io.hyperswitch.paymentsheet

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class AddressElementActivityContract :
    ActivityResultContract<AddressElementActivityContract.Args, AddressLauncherResult>() {

    override fun createIntent(context: Context, input: Args) = Intent()

    override fun parseResult(resultCode: Int, intent: Intent?) = AddressLauncherResult.Canceled

    /**
     * Arguments for launching [AddressElementActivity] to collect an address.
     *
     * @param publishableKey the Hyperswitch publishable key
     * @param config the paymentsheet configuration passed from the merchant
     */
    @Parcelize
    data class Args internal constructor(
        internal val publishableKey: String,
        internal val config: AddressLauncher.Configuration?,
    ) : Parcelable
}
