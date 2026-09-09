package io.hyperswitch.paymentmethods

/**
 * Mirrors the payment-methods package's `FieldStyles`/`Appearance`/`FieldOptions` types
 * (`core/types.ts`) so the native side can express the same customization surface with
 * real typed classes instead of raw maps. Every `toMap()` below produces exactly the JSON
 * shape the JS side expects at `configuration.styles` / `configuration.options` /
 * `configuration.appearance.variables`.
 */

/** A ViewStyle-shaped slot (`root`, `container`, `accessory`). */
data class ViewStyleProps(
    val backgroundColor: Int? = null,
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    val borderColor: Int? = null,
    val padding: Float? = null,
    val height: Float? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        backgroundColor?.let { put("backgroundColor", it) }
        borderRadius?.let { put("borderRadius", it) }
        borderWidth?.let { put("borderWidth", it) }
        borderColor?.let { put("borderColor", it) }
        padding?.let { put("padding", it) }
        height?.let { put("height", it) }
    }

    internal fun isEmpty(): Boolean = toMap().isEmpty()
}

/** A TextStyle-shaped slot (`input`, `placeholder`, `label`, `error`). */
data class TextStyleProps(
    val color: Int? = null,
    val fontSize: Float? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        color?.let { put("color", it) }
        fontSize?.let { put("fontSize", it) }
    }

    internal fun isEmpty(): Boolean = toMap().isEmpty()
}

/** Mirrors `FieldStyles` — every style slot a field can be given. */
data class FieldStyles(
    val root: ViewStyleProps? = null,
    val container: ViewStyleProps? = null,
    val input: TextStyleProps? = null,
    val placeholder: TextStyleProps? = null,
    val label: TextStyleProps? = null,
    val error: TextStyleProps? = null,
    val accessory: ViewStyleProps? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        root?.takeIf { !it.isEmpty() }?.let { put("root", it.toMap()) }
        container?.takeIf { !it.isEmpty() }?.let { put("container", it.toMap()) }
        input?.takeIf { !it.isEmpty() }?.let { put("input", it.toMap()) }
        placeholder?.takeIf { !it.isEmpty() }?.let { put("placeholder", it.toMap()) }
        label?.takeIf { !it.isEmpty() }?.let { put("label", it.toMap()) }
        error?.takeIf { !it.isEmpty() }?.let { put("error", it.toMap()) }
        accessory?.takeIf { !it.isEmpty() }?.let { put("accessory", it.toMap()) }
    }

    internal fun isEmpty(): Boolean = toMap().isEmpty()
}

/** Mirrors `BrandIconMode` (`core/types.ts`). */
enum class BrandIconMode(internal val wireValue: String) {
    STANDARD("standard"),
    ANIMATED("animated"),
    HIDDEN("hidden"),
    HIDE_GENERIC("hideGeneric"),
}

/** Mirrors `CvcIconDisplay`. */
enum class CvcIconDisplay(internal val wireValue: String) {
    HIDDEN("hidden"),
    DEFAULT("default"),
}

/** Mirrors `LabelBehavior`. */
enum class LabelBehavior(internal val wireValue: String) {
    ABOVE("above"),
    FLOATING("floating"),
    NEVER("never"),
}

/** Mirrors `ErrorDisplay`. */
enum class ErrorDisplay(internal val wireValue: String) {
    NONE("none"),
    COLOR_ONLY("colorOnly"),
    INLINE("inline"),
}

/**
 * Mirrors `FieldOptions` — non-style per-field configuration. `cardBrandIcon` is only
 * honored on the `cardNumber` field, `cvcIcon` only on `cardCvc` (same as the JS side).
 */
data class FieldOptions(
    val label: String? = null,
    val labelBehavior: LabelBehavior? = null,
    val errorDisplay: ErrorDisplay? = null,
    val unstyled: Boolean? = null,
    val accessibilityLabel: String? = null,
    val accessibilityHint: String? = null,
    val cardBrandIcon: BrandIconMode? = null,
    val cvcIcon: CvcIconDisplay? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        label?.let { put("label", it) }
        labelBehavior?.let { put("labelBehavior", it.wireValue) }
        errorDisplay?.let { put("errorDisplay", it.wireValue) }
        unstyled?.let { put("unstyled", it) }
        accessibilityLabel?.let { put("accessibilityLabel", it) }
        accessibilityHint?.let { put("accessibilityHint", it) }
        cardBrandIcon?.let { put("cardBrandIcon", it.wireValue) }
        cvcIcon?.let { put("cvcIcon", it.wireValue) }
    }

    internal fun isEmpty(): Boolean = toMap().isEmpty()
}

/**
 * Mirrors `AppearanceVariables` — flat theming primitives forwarded to a vault's own
 * native/webview rendering. Only honored by the `hyperswitch` vault adapter today; other
 * vault types ignore fields they don't support. Set on [PaymentMethodSession.createCardForm]
 * (session-level — there's no per-field equivalent, matching the JS `Appearance.variables`
 * shape it mirrors).
 */
data class AppearanceVariables(
    val colorPrimary: String? = null,
    val colorText: String? = null,
    val colorDanger: String? = null,
    val colorTextPlaceholder: String? = null,
    val colorBackground: String? = null,
    val borderColor: String? = null,
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    val fontFamily: String? = null,
    val fontScale: Float? = null,
    val inputFieldHeight: Float? = null,
    val gap: Float? = null,
    val placeholderTextSizeAdjust: Float? = null,
    val errorTextSizeAdjust: Float? = null,
    val errorMessageSpacing: Float? = null,
    val cardBrandIcon: BrandIconMode? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        colorPrimary?.let { put("colorPrimary", it) }
        colorText?.let { put("colorText", it) }
        colorDanger?.let { put("colorDanger", it) }
        colorTextPlaceholder?.let { put("colorTextPlaceholder", it) }
        colorBackground?.let { put("colorBackground", it) }
        borderColor?.let { put("borderColor", it) }
        borderRadius?.let { put("borderRadius", it) }
        borderWidth?.let { put("borderWidth", it) }
        fontFamily?.let { put("fontFamily", it) }
        fontScale?.let { put("fontScale", it) }
        inputFieldHeight?.let { put("inputFieldHeight", it) }
        gap?.let { put("gap", it) }
        placeholderTextSizeAdjust?.let { put("placeholderTextSizeAdjust", it) }
        errorTextSizeAdjust?.let { put("errorTextSizeAdjust", it) }
        errorMessageSpacing?.let { put("errorMessageSpacing", it) }
        cardBrandIcon?.let { put("cardBrandIcon", it.wireValue) }
    }

    internal fun isEmpty(): Boolean = toMap().isEmpty()
}
