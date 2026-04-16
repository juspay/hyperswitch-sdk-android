package io.hyperswitch.tapcard

/**
 * Configuration for TapCard NFC card reading operations.
 *
 * @property timeoutMs Maximum time to wait for card reading operations (default: 30 seconds)
 * @property enableDebug Enable debug logging for troubleshooting (default: false)
 * @property tryDirectReadOnFailure If true, attempts direct record reading when normal flow fails (default: true)
 * @property continueOnTagNotSupported If true, keeps listening when a non-supported tag is detected (default: false)
 */
data class TapCardConfig(
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val enableDebug: Boolean = false,
    val tryDirectReadOnFailure: Boolean = DEFAULT_TRY_DIRECT_READ,
    val continueOnTagNotSupported: Boolean = DEFAULT_CONTINUE_ON_TAG_NOT_SUPPORTED
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_TRY_DIRECT_READ = true
        const val DEFAULT_CONTINUE_ON_TAG_NOT_SUPPORTED = false
    }
}

/**
 * Builder class for creating [TapCardConfig] instances.
 *
 * Example usage:
 * ```kotlin
 * val config = TapCardConfigBuilder()
 *     .setTimeout(30_000)
 *     .enableDebug(BuildConfig.DEBUG)
 *     .build()
 * ```
 */
class TapCardConfigBuilder {
    private var timeoutMs: Long = TapCardConfig.DEFAULT_TIMEOUT_MS
    private var enableDebug: Boolean = false
    private var tryDirectReadOnFailure: Boolean = TapCardConfig.DEFAULT_TRY_DIRECT_READ
    private var continueOnTagNotSupported: Boolean = TapCardConfig.DEFAULT_CONTINUE_ON_TAG_NOT_SUPPORTED

    /**
     * Sets the timeout duration for card reading operations.
     *
     * @param timeoutMs Timeout in milliseconds
     * @return This builder for chaining
     */
    fun setTimeout(timeoutMs: Long) = apply {
        this.timeoutMs = timeoutMs
    }

    /**
     * Enables or disables debug logging.
     *
     * @param enable true to enable debug logs, false to disable
     * @return This builder for chaining
     */
    fun enableDebug(enable: Boolean) = apply {
        this.enableDebug = enable
    }

    /**
     * Controls whether to attempt direct record reading when normal EMV flow fails.
     * When enabled (default), the reader will try fallback strategies like direct
     * SFI record reading if GPO or PPSE selection fails.
     *
     * @param enable true to enable fallback reading, false to fail fast
     * @return This builder for chaining
     */
    fun tryDirectReadOnFailure(enable: Boolean) = apply {
        this.tryDirectReadOnFailure = enable
    }

    /**
     * Controls whether to keep listening when a non-supported tag is detected.
     * When enabled, tapping an unsupported tag (e.g., transport card, access card)
     * will notify the callback with TagNotSupported but continue listening for
     * subsequent taps. When disabled (default), listening stops on any result.
     *
     * @param enable true to continue listening on unsupported tags
     * @return This builder for chaining
     */
    fun continueOnTagNotSupported(enable: Boolean) = apply {
        this.continueOnTagNotSupported = enable
    }

    /**
     * Builds and returns a [TapCardConfig] instance.
     *
     * @return The configured TapCardConfig
     */
    fun build(): TapCardConfig {
        return TapCardConfig(
            timeoutMs = timeoutMs,
            enableDebug = enableDebug,
            tryDirectReadOnFailure = tryDirectReadOnFailure,
            continueOnTagNotSupported = continueOnTagNotSupported
        )
    }
}
