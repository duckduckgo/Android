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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Transparent
import com.duckduckgo.common.ui.compose.theme.asTextStyle

/**
 * Base text field component for the DuckDuckGo design system that allows user input.
 * See also [DaxSecureTextField] for entering sensitive information like passwords.
 *
 * @param state The state of the text field that is used to read and write the text and selection.
 * @param modifier Optional [Modifier] for this text field. Can be used request focus via [Modifier.focusRequester] for example.
 * @param hint Optional hint text to display inside the text field when it's empty or above the text field when it has text or is focused.
 * @param lineLimits Line limits configuration for the text field, such as single-line, multi-line or form. See [DaxTextFieldLineLimits] for details.
 * @param enabled Whether the interaction with the text field is enabled or disabled.
 * @param editable Whether the text field is editable by the user or read-only.
 * @param clickable Whether the text field is clickable, triggering the [onClick] callback when focused.
 * @param error Optional error message to display below the text field. If provided, the text field will be styled to indicate an error.
 * @param keyboardOptions Software keyboard options that contains configuration such as [KeyboardType] and [ImeAction].
 * See [DaxTextFieldDefaults.TextKeyboardOptions], [DaxTextFieldDefaults.IpAddressKeyboardOptions] and [DaxTextFieldDefaults.UrlKeyboardOptions] for examples.
 * @param trailingIcon Optional [DaxTextFieldTrailingIcon] that will be displayed at the end of the text field.
 * @param inputTransformation Optional transformation to apply to the input text before it's written to the state. Can be used for input filtering.
 * @param onClick Optional callback that is triggered when the text field is [clickable] or when [trailingIcon] is clicked.
 * @param onFocusChanged Optional callback that is triggered when the focus state of the text field changes.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1212213756433276?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=auto&node-id=2691-3327
 */
@Composable
fun DaxTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    hint: String? = null,
    lineLimits: DaxTextFieldLineLimits = DaxTextFieldLineLimits.MultiLine,
    enabled: Boolean = true,
    editable: Boolean = true,
    clickable: Boolean = false,
    error: String? = null,
    trailingIcon: DaxTextFieldTrailingIcon? = null,
    keyboardOptions: KeyboardOptions = DaxTextFieldDefaults.TextKeyboardOptions,
    inputTransformation: InputTransformation? = null,
    onClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    // override is needed as TextField uses MaterialTheme.typography internally for animating the label text style
    MaterialTheme(
        typography = Typography(
            bodySmall = DuckDuckGoTheme.typography.caption.asTextStyle.copy(
                fontFamily = FontFamily.Default,
                color = DuckDuckGoTheme.colors.text.secondary,
            ),
            bodyLarge = DuckDuckGoTheme.typography.body1.asTextStyle.copy(
                fontFamily = FontFamily.Default,
                color = DuckDuckGoTheme.colors.text.secondary,
            ),
        ),
    ) {
        OutlinedTextField(
            state = state,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                    if (focusState.isFocused && clickable) {
                        onClick?.invoke()
                    }
                }
                .alpha(
                    if (enabled || (!editable && clickable)) {
                        DaxTextFieldDefaults.ALPHA_ENABLED
                    } else {
                        DaxTextFieldDefaults.ALPHA_DISABLED
                    },
                ),
            enabled = enabled || editable || clickable,
            readOnly = !editable || clickable,
            label = if (!hint.isNullOrBlank()) {
                {
                    // can't use DaxText here as TextField applies its own style to label and the Text style needs to be Unspecified
                    Text(
                        text = hint,
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
            trailingIcon = trailingIcon?.let { { trailingIcon.ToIcon(enabled, onClick) } },
            isError = !error.isNullOrBlank(),
            shape = DuckDuckGoTheme.shapes.small,
            supportingText = if (!error.isNullOrBlank()) {
                {
                    DaxText(
                        text = error,
                        style = DuckDuckGoTheme.typography.caption,
                        color = DuckDuckGoTheme.colors.destructive,
                    )
                }
            } else {
                null
            },
            keyboardOptions = keyboardOptions,
            lineLimits = lineLimits.toLineLimits(),
            inputTransformation = inputTransformation,
            colors = daxTextFieldColors(),
        )
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

/**
 * Represents a trailing icon for the text field.
 *
 * @param iconResId Resource ID of the drawable icon.
 * @param contentDescription Optional content description for accessibility.
 */
@Stable
data class DaxTextFieldTrailingIcon(
    @DrawableRes val iconResId: Int,
    val contentDescription: String? = null,
)

/**
 * Converts a [DaxTextFieldTrailingIcon] to a [IconButton] composable.
 */
@Composable
internal fun DaxTextFieldTrailingIcon.ToIcon(
    enabled: Boolean,
    onClick: (() -> Unit)?,
) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = DuckDuckGoTheme.colors.iconPrimary,
        )
    }
}

@Composable
internal fun daxTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DuckDuckGoTheme.colors.text.primary,
    unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
    disabledTextColor = DuckDuckGoTheme.colors.text.primary,
    errorTextColor = DuckDuckGoTheme.colors.text.primary,
    focusedContainerColor = Transparent,
    unfocusedContainerColor = Transparent,
    disabledContainerColor = Transparent,
    errorContainerColor = Transparent,
    focusedBorderColor = DuckDuckGoTheme.colors.accentBlue,
    unfocusedBorderColor = DuckDuckGoTheme.colors.borders,
    disabledBorderColor = DuckDuckGoTheme.colors.borders,
    errorBorderColor = DuckDuckGoTheme.colors.destructive,
    focusedLabelColor = DuckDuckGoTheme.colors.accentBlue,
    unfocusedLabelColor = DuckDuckGoTheme.colors.text.secondary,
    disabledLabelColor = DuckDuckGoTheme.colors.text.secondary,
    errorLabelColor = DuckDuckGoTheme.colors.destructive,
    focusedTrailingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    unfocusedTrailingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    disabledTrailingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    errorTrailingIconColor = DuckDuckGoTheme.colors.destructive,
    focusedSupportingTextColor = DuckDuckGoTheme.colors.destructive,
    unfocusedSupportingTextColor = DuckDuckGoTheme.colors.destructive,
    disabledSupportingTextColor = DuckDuckGoTheme.colors.destructive,
    errorSupportingTextColor = DuckDuckGoTheme.colors.destructive,
    cursorColor = DuckDuckGoTheme.colors.accentBlue,
    errorCursorColor = DuckDuckGoTheme.colors.destructive,
    selectionColors = TextSelectionColors(
        handleColor = DuckDuckGoTheme.colors.accentBlue,
        backgroundColor = DuckDuckGoTheme.colors.accentBlue.copy(alpha = DaxTextFieldDefaults.ALPHA_DISABLED),
    ),
    focusedLeadingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    unfocusedLeadingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    disabledLeadingIconColor = DuckDuckGoTheme.colors.iconPrimary,
    errorLeadingIconColor = DuckDuckGoTheme.colors.destructive,
    focusedPrefixColor = DuckDuckGoTheme.colors.text.secondary,
    unfocusedPrefixColor = DuckDuckGoTheme.colors.text.secondary,
    disabledPrefixColor = DuckDuckGoTheme.colors.text.secondary,
    errorPrefixColor = DuckDuckGoTheme.colors.text.secondary,
    focusedSuffixColor = DuckDuckGoTheme.colors.text.secondary,
    unfocusedSuffixColor = DuckDuckGoTheme.colors.text.secondary,
    disabledSuffixColor = DuckDuckGoTheme.colors.text.secondary,
    errorSuffixColor = DuckDuckGoTheme.colors.text.secondary,
    focusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
    unfocusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
    disabledPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
    errorPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
)
