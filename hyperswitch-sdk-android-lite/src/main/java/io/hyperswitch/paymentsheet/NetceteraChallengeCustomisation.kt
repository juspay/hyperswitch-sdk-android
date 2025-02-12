package io.hyperswitch.paymentsheet

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize



@Parcelize
data class LabelCustomization(
    val textFontName: String? = null,
    val textColor: String? = null,
    val textFontSize: Float? = null,
    val headingTextFontName: String? = null,
    val headingTextColor: String? = null,
    val headingTextFontSize: Float? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            textFontName?.let { putString("textFontName", it) }
            textColor?.let { putString("textColor", it) }
            textFontSize?.let { putFloat("textFontSize", it) }
            headingTextFontName?.let { putString("headingTextFontName", it) }
            headingTextColor?.let { putString("headingTextColor", it) }
            headingTextFontSize?.let { putFloat("headingTextFontSize", it) }
        }

    class Builder {
        private var textFontName: String? = null
        private var textColor: String? = null
        private var textFontSize: Float? = null
        private var headingTextFontName: String? = null
        private var headingTextColor: String? = null
        private var headingTextFontSize: Float? = null

        fun textFontName(name: String) = apply { this.textFontName = name }
        fun textColor(color: String) = apply { this.textColor = color }
        fun textFontSize(size: Float) = apply { this.textFontSize = size }
        fun headingTextFontName(name: String) = apply { this.headingTextFontName = name }
        fun headingTextColor(color: String) = apply { this.headingTextColor = color }
        fun headingTextFontSize(size: Float) = apply { this.headingTextFontSize = size }

        fun build() = LabelCustomization(
            textFontName = textFontName,
            textColor = textColor,
            textFontSize = textFontSize,
            headingTextFontName = headingTextFontName,
            headingTextColor = headingTextColor,
            headingTextFontSize = headingTextFontSize
        )
    }
}


@Parcelize
data class TextBoxCustomization(
    val textFontName: String? = null,
    val textColor: String? = null,
    val textFontSize: Float? = null,
    val borderWidth: Float? = null,
    val borderColor: String? = null,
    val cornerRadius: Float? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            textFontName?.let { putString("textFontName", it) }
            textColor?.let { putString("textColor", it) }
            textFontSize?.let { putFloat("textFontSize", it) }
            borderWidth?.let { putFloat("borderWidth", it) }
            borderColor?.let { putString("borderColor", it) }
            cornerRadius?.let { putFloat("cornerRadius", it) }
        }

    class Builder {
        private var textFontName: String? = null
        private var textColor: String? = null
        private var textFontSize: Float? = null
        private var borderWidth: Float? = null
        private var borderColor: String? = null
        private var cornerRadius: Float? = null

        fun textFontName(name: String) = apply { this.textFontName = name }
        fun textColor(color: String) = apply { this.textColor = color }
        fun textFontSize(size: Float) = apply { this.textFontSize = size }
        fun borderWidth(width: Float) = apply { this.borderWidth = width }
        fun borderColor(color: String) = apply { this.borderColor = color }
        fun cornerRadius(radius: Float) = apply { this.cornerRadius = radius }

        fun build() = TextBoxCustomization(
            textFontName, textColor, textFontSize, borderWidth, borderColor, cornerRadius
        )
    }
}


@Parcelize
data class ToolbarCustomization(
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val buttonText: String? = null,
    val headerText: String? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            backgroundColor?.let { putString("backgroundColor", it) }
            textColor?.let { putString("textColor", it) }
            buttonText?.let { putString("buttonText", it) }
            headerText?.let { putString("headerText", it) }
        }

    class Builder {
        private var backgroundColor: String? = null
        private var textColor: String? = null
        private var buttonText: String? = null
        private var headerText: String? = null

        fun backgroundColor(color: String) = apply { this.backgroundColor = color }
        fun textColor(color: String) = apply { this.textColor = color }
        fun buttonText(text: String) = apply { this.buttonText = text }
        fun headerText(text: String) = apply { this.headerText = text }

        fun build() = ToolbarCustomization(backgroundColor, textColor, buttonText, headerText)
    }
}


@Parcelize
data class ButtonCustomization(
    val backgroundColor: String? = null,
    val cornerRadius: Float? = null,
    val textFontSize: Float? = null,
    val textColor: String? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            backgroundColor?.let { putString("backgroundColor", it) }
            cornerRadius?.let { putFloat("cornerRadius", it) }
            textFontSize?.let { putFloat("textFontSize", it) }
            textColor?.let { putString("textColor", it) }
        }

    class Builder {
        private var backgroundColor: String? = null
        private var cornerRadius: Float? = null
        private var textFontSize: Float? = null
        private var textColor: String? = null

        fun backgroundColor(color: String) = apply { this.backgroundColor = color }
        fun cornerRadius(radius: Float) = apply { this.cornerRadius = radius }
        fun textFontSize(size: Float) = apply { this.textFontSize = size }
        fun textColor(color: String) = apply { this.textColor = color }

        fun build() = ButtonCustomization(
            backgroundColor, cornerRadius, textFontSize, textColor
        )
    }
}


@Parcelize
data class ViewCustomization(
    val challengeViewBackgroundColor: String? = null,
    val progressViewBackgroundColor: String? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            challengeViewBackgroundColor?.let { putString("challengeViewBackgroundColor", it) }
            progressViewBackgroundColor?.let { putString("progressViewBackgroundColor", it) }
        }

    class Builder {
        private var challengeViewBackgroundColor: String? = null
        private var progressViewBackgroundColor: String? = null

        fun challengeViewBackgroundColor(color: String) = apply {
            this.challengeViewBackgroundColor = color
        }

        fun progressViewBackgroundColor(color: String) = apply {
            this.progressViewBackgroundColor = color
        }

        fun build(): ViewCustomization {
            return ViewCustomization(
                challengeViewBackgroundColor,
                progressViewBackgroundColor
            )
        }
    }
}


@Parcelize
data class NetceteraChallengeUICustomization(
    /**
     * Describes the customization for label appearance.
     */
    val labelCustomization: LabelCustomization? = null,

    /**
     * Describes the customization for text box appearance.
     */
    val textBoxCustomization: TextBoxCustomization? = null,

    /**
     * Describes the customization for toolbar appearance.
     */
    val toolbarCustomization: ToolbarCustomization? = null,

    /**
     * Describes the customization for the submit button.
     */
    val submitButtonCustomization: ButtonCustomization? = null,

    /**
     * Describes the customization for the cancel button.
     */
    val cancelButtonCustomization: ButtonCustomization? = null,

    /**
     * Describes the customization for view appearance.
     */
    val viewCustomization: ViewCustomization? = null
) : Parcelable {
    val bundle: Bundle
        get() = Bundle().apply {
            labelCustomization?.let { putBundle("labelCustomization", it.bundle) }
            textBoxCustomization?.let { putBundle("textBoxCustomization", it.bundle) }
            toolbarCustomization?.let { putBundle("toolbarCustomization", it.bundle) }
            submitButtonCustomization?.let { putBundle("submitButtonCustomization", it.bundle) }
            cancelButtonCustomization?.let { putBundle("cancelButtonCustomization", it.bundle) }
            viewCustomization?.let { putBundle("viewCustomization", it.bundle) }
        }

    class Builder {
        private var labelCustomization: LabelCustomization? = null
        private var textBoxCustomization: TextBoxCustomization? = null
        private var toolbarCustomization: ToolbarCustomization? = null
        private var submitButtonCustomization: ButtonCustomization? = null
        private var cancelButtonCustomization: ButtonCustomization? = null
        private var viewCustomization: ViewCustomization? = null

        fun labelCustomization(labelCustomization: LabelCustomization) = apply {
            this.labelCustomization = labelCustomization
        }

        fun textBoxCustomization(textBoxCustomization: TextBoxCustomization) = apply {
            this.textBoxCustomization = textBoxCustomization
        }

        fun toolbarCustomization(toolbarCustomization: ToolbarCustomization) = apply {
            this.toolbarCustomization = toolbarCustomization
        }

        fun submitButtonCustomization(submitButtonCustomization: ButtonCustomization) = apply {
            this.submitButtonCustomization = submitButtonCustomization
        }

        fun cancelButtonCustomization(cancelButtonCustomization: ButtonCustomization) = apply {
            this.cancelButtonCustomization = cancelButtonCustomization
        }

        fun viewCustomization(viewCustomization: ViewCustomization) = apply {
            this.viewCustomization = viewCustomization
        }

        fun build() = NetceteraChallengeUICustomization(
            labelCustomization,
            textBoxCustomization,
            toolbarCustomization,
            submitButtonCustomization,
            cancelButtonCustomization,
            viewCustomization
        )
    }
}


@Parcelize
data class NetceteraChallengeUIWrapper(
    val lightModeCustomization: NetceteraChallengeUICustomization? = null,
    val darkModeCustomization: NetceteraChallengeUICustomization? = null
) : Parcelable {

    fun getCustomization(isDarkMode: Boolean): NetceteraChallengeUICustomization? {
        return if (isDarkMode) darkModeCustomization ?: lightModeCustomization
        else lightModeCustomization ?: darkModeCustomization
    }

    val bundle: Bundle
        get() = Bundle().apply {
            lightModeCustomization?.let { putBundle("lightModeCustomization", it.bundle) }
            darkModeCustomization?.let { putBundle("darkModeCustomization", it.bundle) }
        }

    class Builder {
        private var lightModeCustomization: NetceteraChallengeUICustomization? = null
        private var darkModeCustomization: NetceteraChallengeUICustomization? = null

        fun lightModeCustomization(customization: NetceteraChallengeUICustomization) = apply {
            this.lightModeCustomization = customization
        }

        fun darkModeCustomization(customization: NetceteraChallengeUICustomization) = apply {
            this.darkModeCustomization = customization
        }

        fun build(): NetceteraChallengeUIWrapper {
            require(lightModeCustomization != null || darkModeCustomization != null) {
                "At least one of lightModeCustomization or darkModeCustomization must be set before calling build()"
            }

            return NetceteraChallengeUIWrapper(
                lightModeCustomization,
                darkModeCustomization
            )
        }
    }
}


