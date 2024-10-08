package io.hyperswitch.lite.payments.googlepaylauncher

import com.google.android.gms.wallet.WalletConstants

enum class GooglePayEnvironment(
    internal val value: Int
) {
    Production(WalletConstants.ENVIRONMENT_PRODUCTION),
    Test(WalletConstants.ENVIRONMENT_TEST)
}