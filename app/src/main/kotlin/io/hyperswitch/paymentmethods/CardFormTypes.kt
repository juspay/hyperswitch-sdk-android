package io.hyperswitch.paymentmethods

import android.os.Bundle

enum class CardElementType(internal val wire: String) {
    CARD_NUMBER("cardNumber"),
    CARD_EXPIRY("cardExpiry"),
    CARD_CVC("cardCvc"),
    CARDHOLDER_NAME("cardholderName");

    internal companion object {
        fun of(wire: Any?): CardElementType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * What is known about the card without knowing the card. The number and the security code
 * are never part of any value this SDK hands out.
 */
data class CardDetails(
    val bin: String?,
    val extendedBin: String?,
    val last4: String?,
    val brand: String?,
    val expiryMonth: String?,
    val expiryYear: String?,
) {
    internal companion object {
        fun from(payload: Map<*, *>) = CardDetails(
            bin = payload["bin"] as? String,
            extendedBin = payload["extendedBin"] as? String,
            last4 = payload["last4"] as? String,
            brand = payload["brand"] as? String,
            expiryMonth = payload["expiryMonth"] as? String,
            expiryYear = payload["expiryYear"] as? String,
        )
    }
}

data class CardFieldState(
    val elementType: CardElementType,
    val isEmpty: Boolean,
    val isComplete: Boolean,
    val isValid: Boolean,
    val isTouched: Boolean,
    val brand: String?,
    val error: String?,
) {
    internal companion object {
        fun from(payload: Map<*, *>): CardFieldState? {
            val elementType = CardElementType.of(payload["elementType"]) ?: return null
            return CardFieldState(
                elementType = elementType,
                isEmpty = payload["empty"] as? Boolean ?: true,
                isComplete = payload["complete"] as? Boolean ?: false,
                isValid = payload["valid"] as? Boolean ?: false,
                isTouched = payload["touched"] as? Boolean ?: false,
                brand = payload["brand"] as? String,
                error = payload["error"] as? String,
            )
        }
    }
}

data class CardFormState(
    /** Every field on screen is complete. */
    val isComplete: Boolean,
    /** Every field on screen is valid. */
    val isValid: Boolean,
    val card: CardDetails,
    /** The fields currently on screen, by type. */
    val fields: Map<CardElementType, CardFieldState>,
) {
    internal companion object {
        fun from(payload: Map<*, *>) = CardFormState(
            isComplete = payload["complete"] as? Boolean ?: false,
            isValid = payload["valid"] as? Boolean ?: false,
            card = CardDetails.from(payload["payload"] as? Map<*, *> ?: emptyMap<String, Any?>()),
            fields = (payload["fields"] as? Map<*, *>).orEmpty().values
                .mapNotNull { (it as? Map<*, *>)?.let(CardFieldState::from) }
                .associateBy { it.elementType },
        )
    }
}

data class CardFormError(val message: String)

data class TokenizedCard(
    val vaultType: String?,
    /** The vault's tokens by name. The Hyperswitch vault returns `payment_method_token`. */
    val tokens: Map<String, String>,
    val card: CardDetails?,
) {
    val paymentMethodToken: String? get() = tokens["payment_method_token"]
}

data class TokenizeError(val kind: Kind, val code: String, val message: String) {
    enum class Kind(internal val wire: String) {
        /** The card as entered cannot be tokenized; the fields say why. */
        VALIDATION("validation_error"),
        API("api_error"),
        CARD("card_error");

        internal companion object {
            fun of(wire: Any?): Kind? = entries.firstOrNull { it.wire == wire }
        }
    }

    internal companion object {
        fun local(code: String, message: String) = TokenizeError(Kind.VALIDATION, code, message)
    }
}

sealed interface TokenizeResult {
    data class Success(val card: TokenizedCard) : TokenizeResult
    data class Failure(val error: TokenizeError) : TokenizeResult
}

private const val TOKENIZE_FALLBACK = "The card could not be tokenized."

/** From the bundle's answer to a tokenize command (`CommandResultPayload`). */
internal fun tokenizeResultOf(payload: Map<*, *>): TokenizeResult {
    val result = payload["result"] as? Map<*, *>
    if (payload["ok"] != true || result == null) {
        return TokenizeResult.Failure(
            TokenizeError(TokenizeError.Kind.API, "tokenization_failed", payload["message"] as? String ?: TOKENIZE_FALLBACK)
        )
    }
    if (result["status"] != "success") {
        val error = result["error"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return TokenizeResult.Failure(
            TokenizeError(
                kind = TokenizeError.Kind.of(error["type"]) ?: TokenizeError.Kind.API,
                code = error["code"] as? String ?: "tokenization_failed",
                message = error["message"] as? String ?: TOKENIZE_FALLBACK,
            )
        )
    }
    val tokens = ((result["data"] as? Map<*, *>)?.get("tokens") as? Map<*, *>).orEmpty()
        .mapNotNull { (name, value) -> (name as? String)?.let { it to (value as? String ?: value.toString()) } }
        .toMap()
    return TokenizeResult.Success(
        TokenizedCard(
            vaultType = result["vaultType"] as? String,
            tokens = tokens,
            card = (result["card"] as? Map<*, *>)?.let(CardDetails::from),
        )
    )
}

data class CardFieldOptions(
    val placeholder: String? = null,
    val label: String? = null,
    val labelBehavior: LabelBehavior? = null,
    val errorDisplay: ErrorDisplay? = null,
    val unstyled: Boolean? = null,
    /** Read by the CVC field only. */
    val cvcIcon: CvcIcon? = null,
) {
    enum class LabelBehavior(internal val wire: String) { ABOVE("above"), FLOATING("floating"), NEVER("never") }
    enum class ErrorDisplay(internal val wire: String) { NONE("none"), COLOR_ONLY("colorOnly"), INLINE("inline") }
    enum class CvcIcon(internal val wire: String) { STANDARD("default"), HIDDEN("hidden") }

    internal fun toBundle() = Bundle().apply {
        placeholder?.let { putString("placeholder", it) }
        label?.let { putString("label", it) }
        labelBehavior?.let { putString("labelBehavior", it.wire) }
        errorDisplay?.let { putString("errorDisplay", it.wire) }
        unstyled?.let { putBoolean("unstyled", it) }
        cvcIcon?.let { putString("cvcIcon", it.wire) }
    }
}
