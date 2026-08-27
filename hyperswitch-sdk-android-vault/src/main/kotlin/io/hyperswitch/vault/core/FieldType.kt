package io.hyperswitch.vault.core

enum class FieldType(val rawValue: String) {
    CARD_NUMBER("card_number"),
    CARD_EXPIRATION_DATE("exp_date"),
    CARD_CVC("cvc"),
    CARD_HOLDER_NAME("card_holder"),
    SSN("ssn"),
    INFO("info");

    companion object {
        fun fromRawValue(rawValue: String?): FieldType? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}
