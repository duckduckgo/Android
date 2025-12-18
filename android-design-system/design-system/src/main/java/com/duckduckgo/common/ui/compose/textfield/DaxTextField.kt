/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.common.ui.compose.textfield

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Transparent
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Base text field component for the DuckDuckGo design system that allows user input.
 * See also [DaxSecureTextField] for entering sensitive information like passwords.
 *
 * @param state The state of the text field that is used to read and write the text and selection.
 * @param modifier Optional [Modifier] for this text field. Can be used request focus via [Modifier.focusRequester] for example.
 * @param label Optional label/hint text to display inside the text field when it's empty or above the text field when it has text or is focused.
 * @param lineLimits Line limits configuration for the text field, such as single-line, multi-line or form. See [DaxTextFieldLineLimits] for details.
 * @param inputMode Input mode for the text field, such as editable, read-only or disabled. See [DaxTextFieldInputMode] for details.
 * @param error Optional error message to display below the text field. If provided, the text field will be styled to indicate an error.
 * @param keyboardOptions Software keyboard options that contains configuration such as [KeyboardType] and [ImeAction].
 * See [DaxTextFieldDefaults.TextKeyboardOptions], [DaxTextFieldDefaults.IpAddressKeyboardOptions] and
 * [DaxTextFieldDefaults.UrlKeyboardOptions] for examples.
 * @param inputTransformation Optional transformation to apply to the input text before it's written to the state. Can be used for input filtering.
 * @param interactionSource Optional interaction source for observing and emitting interaction events.
 * You can use this to observe focus, pressed, hover and drag events.
 * @param trailingIcon Optional trailing icon composable to display at the end of the text field.
 * Use [DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon] to create the icon.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1212213756433276?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=auto&node-id=2691-3327
 */
@Composable
fun DaxTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = null,
    lineLimits: DaxTextFieldLineLimits = DaxTextFieldLineLimits.MultiLine,
    inputMode: DaxTextFieldInputMode = DaxTextFieldInputMode.Editable,
    error: String? = null,
    keyboardOptions: KeyboardOptions = DaxTextFieldDefaults.TextKeyboardOptions,
    inputTransformation: InputTransformation? = null,
    interactionSource: MutableInteractionSource? = null,
    trailingIcon: (@Composable DaxTextFieldTrailingIconScope.() -> Unit)? = null,
) {
    val daxTextFieldColors = daxTextFieldColors()

    // override is needed as TextField uses MaterialTheme.typography internally for animating the label text style
    MaterialTheme(
        typography = Typography(
            bodySmall = DuckDuckGoTheme.typography.caption.asTextStyle.copy(
                fontFamily = FontFamily.Default,
                color = DuckDuckGoTheme.textColors.secondary,
            ),
            bodyLarge = DuckDuckGoTheme.typography.body1.asTextStyle.copy(
                fontFamily = FontFamily.Default,
                color = DuckDuckGoTheme.textColors.secondary,
            ),
        ),
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides daxTextFieldColors.textSelectionColors) {
            OutlinedTextField(
                state = state,
                modifier = modifier
                    .fillMaxWidth()
                    .alpha(
                        if (inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly) {
                            DaxTextFieldDefaults.ALPHA_ENABLED
                        } else {
                            DaxTextFieldDefaults.ALPHA_DISABLED
                        },
                    ),
                enabled = when (inputMode) {
                    DaxTextFieldInputMode.Editable -> true
                    DaxTextFieldInputMode.ReadOnly -> true
                    DaxTextFieldInputMode.Disabled -> false
                },
                readOnly = when (inputMode) {
                    DaxTextFieldInputMode.Editable -> false
                    DaxTextFieldInputMode.ReadOnly -> true
                    DaxTextFieldInputMode.Disabled -> true
                },
                label = if (!label.isNullOrBlank()) {
                    {
                        // can't use DaxText here as TextField applies its own style to label and the Text style needs to be Unspecified
                        Text(
                            text = label,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current,
                        )
                    }
                } else {
                    null
                },
                labelPosition = TextFieldLabelPosition.Attached(),
                textStyle = DuckDuckGoTheme.typography.body1.asTextStyle,
                trailingIcon = trailingIcon?.let {
                    {
                        DaxTextFieldTrailingIconScope.it()
                    }
                },
                isError = !error.isNullOrBlank(),
                shape = DuckDuckGoTheme.shapes.small,
                supportingText = if (!error.isNullOrBlank()) {
                    {
                        DaxText(
                            text = error,
                            style = DuckDuckGoTheme.typography.caption,
                            color = DuckDuckGoTheme.textColors.destructive,
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = keyboardOptions,
                lineLimits = lineLimits.toLineLimits(),
                inputTransformation = inputTransformation,
                interactionSource = interactionSource,
                colors = daxTextFieldColors,
            )
        }
    }
}

/**
 * Predefined configurations and types for [DaxTextFieldDefaults].
 */
object DaxTextFieldDefaults {

    internal const val ALPHA_ENABLED = 1f
    internal const val ALPHA_DISABLED = 0.4f

    val TextKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = true,
    )

    val PasswordKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
    )

    val IpAddressKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
    )

    val UrlKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
    )

    /**
     * Input transformation that only allows digits and dots to be entered.
     * Can be used for IP address input fields.
     */
    class IpAddressInputTransformation : InputTransformation {
        override fun TextFieldBuffer.transformInput() {
            val filtered = asCharSequence().filter { it.isDigit() || it == '.' }
            if (filtered.length != length) {
                replace(0, length, filtered)
            }
        }
    }
}

object DaxTextFieldTrailingIconScope {
    /**
     * Represents a trailing icon for the text field.
     *
     * @param painter Painter for the icon to display.
     * @param contentDescription Optional content description for accessibility.
     * @param modifier Optional [Modifier] for this icon button.
     * @param enabled Whether the icon button is enabled or disabled.
     * @param onClick Optional callback that is triggered when the icon button is clicked.
     */
    @Composable
    fun DaxTextFieldTrailingIcon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = enabled,
            modifier = modifier,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = DuckDuckGoTheme.iconColors.primary,
            )
        }
    }
}

@Stable
enum class DaxTextFieldLineLimits {
    /**
     * The TextField will take up a single line and will scroll horizontally if the text is too long.
     */
    SingleLine,

    /**
     * The TextField will start with a one line height and then expand vertically as needed to accommodate the text.
     */
    MultiLine,

    /**
     * The TextField will always take at least 3 lines of height and then expand vertically as needed based on the input.
     */
    Form,

    ;

    /**
     * Converts this [DaxTextFieldLineLimits] to a [TextFieldLineLimits] used by the underlying TextField.
     */
    fun toLineLimits(): TextFieldLineLimits = when (this) {
        SingleLine -> TextFieldLineLimits.SingleLine
        MultiLine -> TextFieldLineLimits.MultiLine(minHeightInLines = 1)
        Form -> TextFieldLineLimits.MultiLine(minHeightInLines = 3)
    }
}

@Stable
enum class DaxTextFieldInputMode {
    /**
     * The TextField is editable by the user.
     */
    Editable,

    /**
     * The TextField is read-only and cannot be edited by the user.
     */
    ReadOnly,

    /**
     * The TextField is disabled and does not allow any interaction.
     */
    Disabled,
}

@Composable
internal fun daxTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DuckDuckGoTheme.textColors.primary,
    unfocusedTextColor = DuckDuckGoTheme.textColors.primary,
    disabledTextColor = DuckDuckGoTheme.textColors.primary,
    errorTextColor = DuckDuckGoTheme.textColors.primary,
    focusedContainerColor = Transparent,
    unfocusedContainerColor = Transparent,
    disabledContainerColor = Transparent,
    errorContainerColor = Transparent,
    focusedBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
    unfocusedBorderColor = DuckDuckGoTheme.colors.textField.borders,
    disabledBorderColor = DuckDuckGoTheme.colors.textField.borders,
    errorBorderColor = DuckDuckGoTheme.textColors.destructive,
    focusedLabelColor = DuckDuckGoTheme.colors.brand.accentBlue,
    unfocusedLabelColor = DuckDuckGoTheme.textColors.secondary,
    disabledLabelColor = DuckDuckGoTheme.textColors.secondary,
    errorLabelColor = DuckDuckGoTheme.textColors.destructive,
    focusedTrailingIconColor = DuckDuckGoTheme.iconColors.primary,
    unfocusedTrailingIconColor = DuckDuckGoTheme.iconColors.primary,
    disabledTrailingIconColor = DuckDuckGoTheme.iconColors.primary,
    errorTrailingIconColor = DuckDuckGoTheme.textColors.destructive,
    focusedSupportingTextColor = DuckDuckGoTheme.textColors.destructive,
    unfocusedSupportingTextColor = DuckDuckGoTheme.textColors.destructive,
    disabledSupportingTextColor = DuckDuckGoTheme.textColors.destructive,
    errorSupportingTextColor = DuckDuckGoTheme.textColors.destructive,
    cursorColor = DuckDuckGoTheme.colors.brand.accentBlue,
    errorCursorColor = DuckDuckGoTheme.textColors.primary,
    selectionColors = TextSelectionColors(
        handleColor = DuckDuckGoTheme.colors.brand.accentBlue,
        backgroundColor = DuckDuckGoTheme.colors.brand.accentBlue.copy(alpha = DaxTextFieldDefaults.ALPHA_DISABLED),
    ),
    focusedLeadingIconColor = DuckDuckGoTheme.iconColors.primary,
    unfocusedLeadingIconColor = DuckDuckGoTheme.iconColors.primary,
    disabledLeadingIconColor = DuckDuckGoTheme.iconColors.primary,
    errorLeadingIconColor = DuckDuckGoTheme.textColors.destructive,
    focusedPrefixColor = DuckDuckGoTheme.textColors.secondary,
    unfocusedPrefixColor = DuckDuckGoTheme.textColors.secondary,
    disabledPrefixColor = DuckDuckGoTheme.textColors.secondary,
    errorPrefixColor = DuckDuckGoTheme.textColors.secondary,
    focusedSuffixColor = DuckDuckGoTheme.textColors.secondary,
    unfocusedSuffixColor = DuckDuckGoTheme.textColors.secondary,
    disabledSuffixColor = DuckDuckGoTheme.textColors.secondary,
    errorSuffixColor = DuckDuckGoTheme.textColors.secondary,
    focusedPlaceholderColor = DuckDuckGoTheme.textColors.secondary,
    unfocusedPlaceholderColor = DuckDuckGoTheme.textColors.secondary,
    disabledPlaceholderColor = DuckDuckGoTheme.textColors.secondary,
    errorPlaceholderColor = DuckDuckGoTheme.textColors.secondary,
)

@PreviewLightDark
@Composable
private fun DaxTextFieldEmptyPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(),
            label = "Enter text",
        )
    }
}

@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxTextFieldWithTextPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Sample text content"),
            label = "Enter text",
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldNoLabelPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Text without label"),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldSingleLinePreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Single line text field"),
            label = "Enter single line",
            lineLimits = DaxTextFieldLineLimits.SingleLine,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldMultiLinePreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Multi line text field\nwith multiple lines\nof content"),
            label = "Enter multiple lines",
            lineLimits = DaxTextFieldLineLimits.MultiLine,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldFormPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Form text field\ntakes minimum 3 lines"),
            label = "Enter form content",
            lineLimits = DaxTextFieldLineLimits.Form,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldEditablePreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Editable text field"),
            label = "Enter text",
            inputMode = DaxTextFieldInputMode.Editable,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldDisabledPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Disabled text field"),
            label = "Enter text",
            inputMode = DaxTextFieldInputMode.Disabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldNonEditablePreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Non-editable text field"),
            label = "Read only",
            inputMode = DaxTextFieldInputMode.ReadOnly,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldWithErrorPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Invalid input"),
            label = "Enter text",
            error = "This field contains an error",
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldWithTrailingIconPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Text with icon"),
            label = "Enter text",
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldEmptyWithTrailingIconPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(),
            label = "Enter text",
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldErrorWithTrailingIconPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Invalid input"),
            label = "Enter text",
            error = "Enter at least 3 characters",
            trailingIcon = {
                DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextFieldDisabledWithTextPreview() {
    DaxTextFieldPreviewBox {
        DaxTextField(
            state = TextFieldState(initialText = "Disabled with text"),
            label = "Enter text",
            inputMode = DaxTextFieldInputMode.Disabled,
        )
    }
}

@Composable
private fun DaxTextFieldPreviewBox(
    content: @Composable () -> Unit,
) {
    DuckDuckGoTheme {
        PreviewBox {
            content()
        }
    }
}
