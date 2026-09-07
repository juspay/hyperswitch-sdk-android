package io.hyperswitch.click_to_pay.utils

import io.hyperswitch.logs.LogUtils.getEnvironment
import io.hyperswitch.logs.SDKEnvironment

class HyperLoaderUtils {
    object Companion {

        fun getBaseUrl(publishableKey: String): String {
            return if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
                "https://sandbox.secure.checkout.visa.com"
            } else {
                "https://secure.checkout.visa.com"
            }
        }

        fun getVisaDirectUrl(publishableKey: String): String {
            return if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
                "https://sandbox-assets.secure.checkout.visa.com/checkout-widget/resources/js/src-i-adapter/visaSdk.js?v2"
            } else {
                "https://assets.secure.checkout.visa.com/checkout-widget/resources/js/src-i-adapter/visaSdk.js?v2"
            }
        }

        fun getMasterCardDirectUrl(publishableKey: String): String {
            return if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
                "https://sandbox.src.mastercard.com/sdk/srcsdk.mastercard.js"
            } else {
                "https://src.mastercard.com/sdk/srcsdk.mastercard.js"
            }
        }

        // URL Helpers
        fun getHyperLoaderURL(publishableKey: String): String {
            return if (getEnvironment(publishableKey) == SDKEnvironment.SANDBOX) {
                "https://beta.hyperswitch.io/web/2025.11.28.12/v1/HyperLoader.js"
            } else {
                "https://checkout.hyperswitch.io/web/2025.11.28.12/v1/HyperLoader.js"
            }
        }
    }
}