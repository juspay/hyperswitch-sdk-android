package io.hyperswitch.vault.core

enum class Environment(val rawValue: String, val jsEnvName: String) {
    SANDBOX("app", "sandbox"),
    PRODUCTION("live", "production"),
    INTEG("integ", "integration");


    fun resolveBaseUrl(): String =
        "https://$rawValue.hyperswitch.io"
}
