package io.hyperswitch.paymentmethods

import com.facebook.react.bridge.ReadableMap

/** Mirrors the payment-methods package's `TokenizeErrorCode` union; any code this SDK does
 * not (yet) know about — a newer package version, or a malformed/missing result — maps to
 * [UNKNOWN] rather than failing to parse. */
enum class TokeniseErrorCode {
    VALIDATION_ERROR,
    INCOMPLETE_FIELD_SET,
    SDK_NOT_READY,
    UNSUPPORTED_CONFIGURATION,
    SESSION_EXPIRED,
    SESSION_CONSUMED,
    INVALID_SESSION,
    UNKNOWN_OUTCOME,
    TOKENIZATION_FAILED,
    UNKNOWN;

    internal companion object {
        fun from(code: String?): TokeniseErrorCode = when (code) {
            "validation_error" -> VALIDATION_ERROR
            "incomplete_field_set" -> INCOMPLETE_FIELD_SET
            "sdk_not_ready" -> SDK_NOT_READY
            "unsupported_configuration" -> UNSUPPORTED_CONFIGURATION
            "session_expired" -> SESSION_EXPIRED
            "session_consumed" -> SESSION_CONSUMED
            "invalid_session" -> INVALID_SESSION
            "unknown_outcome" -> UNKNOWN_OUTCOME
            "tokenization_failed" -> TOKENIZATION_FAILED
            else -> UNKNOWN
        }
    }
}

data class TokeniseError(
    val code: TokeniseErrorCode,
    val message: String?,
    val type: String?,
)

data class TokeniseCard(
    val last4: String?,
    val brand: String?,
    val expiryMonth: String?,
    val expiryYear: String?,
)

/** Result of [CardForm.tokenise]. */
sealed class TokeniseResult {
    data class Success(
        val token: String,
        val vaultType: String?,
        val card: TokeniseCard?,
    ) : TokeniseResult()

    data class Failure(
        val vaultType: String?,
        val error: TokeniseError,
    ) : TokeniseResult()

    internal companion object {
        fun from(raw: ReadableMap?): TokeniseResult {
            if (raw == null) {
                return Failure(
                    vaultType = null,
                    error = TokeniseError(TokeniseErrorCode.UNKNOWN, "No result received", null),
                )
            }

            val status = if (raw.hasKey("status")) raw.getString("status") else null
            val vaultType = if (raw.hasKey("vaultType")) raw.getString("vaultType") else null

            return if (status == "success") {
                val cardMap = raw.getMap("card")
                Success(
                    token = raw.getString("token") ?: "",
                    vaultType = vaultType,
                    card = cardMap?.let {
                        TokeniseCard(
                            last4 = it.getString("last4"),
                            brand = it.getString("brand"),
                            expiryMonth = it.getString("expiryMonth"),
                            expiryYear = it.getString("expiryYear"),
                        )
                    },
                )
            } else {
                val errorMap = raw.getMap("error")
                Failure(
                    vaultType = vaultType,
                    error = TokeniseError(
                        code = TokeniseErrorCode.from(errorMap?.getString("code")),
                        message = errorMap?.getString("message"),
                        type = errorMap?.getString("type"),
                    ),
                )
            }
        }
    }
}
