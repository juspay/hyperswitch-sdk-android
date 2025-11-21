package io.hyperswitch.threeds.models

import androidx.annotation.Keep

/**
 * UI customization options for challenge flow.
 */
@Keep
data class UiCustomization(
    @Keep val toolbarCustomization: ToolbarCustomization?,
    @Keep val labelCustomization: LabelCustomization?,
    @Keep val textBoxCustomization: TextBoxCustomization?,
    @Keep val buttonCustomization: ButtonCustomization?
)

/**
 * Toolbar customization options.
 */
@Keep
data class ToolbarCustomization(
    @Keep val backgroundColor: String?,
    @Keep val headerText: String?,
    @Keep val buttonText: String?,
    @Keep val textColor: String?,
    @Keep val textFontName: String?,
    @Keep val textFontSize: Int?
)

/**
 * Label customization options.
 */
@Keep
data class LabelCustomization(
    @Keep val headingTextColor: String?,
    @Keep val headingTextFontName: String?,
    @Keep val headingTextFontSize: Int?,
    @Keep val textColor: String?,
    @Keep val textFontName: String?,
    @Keep val textFontSize: Int?
)

/**
 * Text box customization options.
 */
@Keep
data class TextBoxCustomization(
    @Keep val textColor: String?,
    @Keep val textFontName: String?,
    @Keep val textFontSize: Int?,
    @Keep val borderColor: String?,
    @Keep val borderWidth: Int?,
    @Keep val cornerRadius: Int?
)

/**
 * Button customization options.
 */
@Keep
data class ButtonCustomization(
    @Keep val backgroundColor: String?,
    @Keep val textColor: String?,
    @Keep val textFontName: String?,
    @Keep val textFontSize: Int?,
    @Keep val cornerRadius: Int?
)
