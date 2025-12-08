package io.hyperswitch.logs

enum class SDKEnvironment {
    PROD,
    SANDBOX
}

object LogUtils {

    fun getEnvironment(publishableKey: String): SDKEnvironment {
        return if (publishableKey.isNotEmpty() && publishableKey.startsWith("pk_prd_")) {
            SDKEnvironment.PROD
        } else {
            SDKEnvironment.SANDBOX
        }
    }

    fun getLoggingUrl(publishableKey: String): String{
        return if (getEnvironment(publishableKey) == SDKEnvironment.PROD)
            "https://api.hyperswitch.io/logs/sdk"
        else
            "https://sandbox.hyperswitch.io/logs/sdk"
    }
}