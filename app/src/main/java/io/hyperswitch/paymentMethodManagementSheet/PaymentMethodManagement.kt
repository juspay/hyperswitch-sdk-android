package io.hyperswitch.paymentMethodManagementSheet

import android.os.Build
import androidx.activity.addCallback
import com.facebook.react.bridge.Arguments
import io.hyperswitch.BuildConfig
import io.hyperswitch.PaymentConfiguration
import io.hyperswitch.PaymentSession.Companion.activity
import io.hyperswitch.paymentsheet.DefaultPaymentSheetLauncher.Companion.context
import io.hyperswitch.react.Utils

class PaymentMethodManagement {

    private fun present(
        ephemeralKey: String,
        sheetType: String?
    ) {

        val hyperParams = Arguments.createMap();
        hyperParams.putString("appId", activity.packageName)
        hyperParams.putString(
            "country", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity.resources.configuration.locales.get(0)?.country
            } else {
                activity.resources.configuration.locale.country
            }
        )
        hyperParams.putString("user-agent", Utils.getUserAgent(activity.applicationContext))
        hyperParams.putString("ip", Utils.getDeviceIPAddress(activity.applicationContext))
        hyperParams.putDouble("launchTime", Utils.getCurrentTime())
        hyperParams.putString("sdkVersion", BuildConfig.VERSION_NAME)

        val map = mapOf(
            "publishableKey" to PaymentConfiguration.pkKey,
            "ephemeralKey" to ephemeralKey,
            "hyperParams" to hyperParams,
            "customBackendUrl" to PaymentConfiguration.cbUrl,
            "customLogUrl" to PaymentConfiguration.logUrl,
            "customParams" to PaymentConfiguration.cParams,
            "type" to sheetType,
        )
        Utils.openReactView(context, map, sheetType ?: "", null)
    }

    fun presentWithEphemeralKey(
        ephemeralKey: String
    ) {
        present(ephemeralKey, "paymentMethodsManagement")
    }
}