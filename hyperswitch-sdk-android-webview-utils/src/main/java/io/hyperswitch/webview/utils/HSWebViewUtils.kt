package io.hyperswitch.webview.utils

import java.net.URI
import java.util.Locale

internal object HSWebViewUtils {
    /** Normalised `scheme://host[:port]` for https URLs without user info; null for anything else. */
    @JvmStatic
    fun httpsOrigin(value: String?): String? {
        if (value == null) return null
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https" || uri.rawUserInfo != null ||
                (uri.port != -1 && uri.port !in 1..65535)) return null
            val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }
}
