package io.hyperswitch.hyperswitch_android_sdk_web.payments.googlepaylauncher

import com.google.android.gms.wallet.WalletConstants

enum class GooglePayEnvironment(
    internal val value: Int
) {
    Production(WalletConstants.ENVIRONMENT_PRODUCTION),
    Test(WalletConstants.ENVIRONMENT_TEST)
}