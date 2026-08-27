package io.hyperswitch.vault.core

enum class Environment(val rawValue: String) {
    SANDBOX("app"),
    PRODUCTION("live"),
    INTEG("integ");


    fun resolveBaseUrl(): String =
        "https://$rawValue.hyperswitch.io"
}
