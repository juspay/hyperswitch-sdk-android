package io.hyperswitch.logs

import android.content.Context
import androidx.core.content.edit
import java.util.Locale
import java.util.UUID

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

    @Synchronized
    fun getOrCreateUniqueKey(context: Context, flow: String): String {
        try {
            val safeFlow = flow.lowercase(Locale.ROOT).replace(" ", "_")
            val prefs = context.applicationContext
                .getSharedPreferences(safeFlow, Context.MODE_PRIVATE)
            val key = "uuid_$safeFlow"

            var uuid = prefs.getString(key, null)

            if (uuid == null) {
                uuid = UUID.randomUUID().toString()
                prefs.edit {
                    putString(key, uuid)
                }
            }
            return uuid
        }catch (_ : Exception){
            return  UUID.randomUUID().toString()
        }
    }
}