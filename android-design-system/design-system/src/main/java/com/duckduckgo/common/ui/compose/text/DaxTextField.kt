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

package com.duckduckgo.common.ui.compose.text

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Transparent
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R

private const val ENABLED_OPACITY = 1f
private const val DISABLED_OPACITY = 0.4f

/**
 * DaxTextField is a custom text field component that matches the functionality of the View-based DaxTextInput.
 *
 * @param value The current text value
 * @param onValueChange Callback triggered when text changes
 * @param modifier Modifier to be applied to the text field
 * @param hint Hint text to display when field is empty
 * @param enabled Whether the field is enabled (affects both editability and visual state)
 * @param isEditable Whether the field is editable (can be used to make field read-only while maintaining enabled appearance)
 * @param error Error message to display, or null if no error
 * @param inputType Type of input field (single line, multiline, password, etc.)
 * @param minLines Minimum number of lines for multiline fields
 * @param endIcon Resource ID for optional trailing icon
 * @param endIconContentDescription Content description for the trailing icon
 * @param onEndIconClick Callback triggered when trailing icon is clicked
 * @param selectAllOnFocus Whether to select all text when field gains focus
 * @param imeAction IME action to display on keyboard
 * @param capitalizeKeyboard Whether to capitalize all input characters
 * @param clickable Whether the field should act as a clickable element (triggers onEndIconClick on focus)
 * @param onFocusChanged Callback triggered when focus state changes
 */
@Composable
fun DaxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    isEditable: Boolean = true,
    error: String? = null,
    inputType: DaxTextFieldType = DaxTextFieldType.MultiLine,
    minLines: Int = 1,
    @DrawableRes endIcon: Int? = null,
    endIconContentDescription: String? = null,
    onEndIconClick: (() -> Unit)? = null,
    selectAllOnFocus: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    capitalizeKeyboard: Boolean = false,
    clickable: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValueState by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    // Handle password visibility based on focus
    LaunchedEffect(isFocused, inputType) {
        if (inputType == DaxTextFieldType.Password) {
            isPasswordVisible = isFocused
        }
    }

    // Handle select all on focus
    LaunchedEffect(isFocused, selectAllOnFocus) {
        if (isFocused && selectAllOnFocus) {
            textFieldValueState = textFieldValueState.copy(
                selection = TextRange(0, textFieldValueState.text.length),
            )
        }
    }

    val keyboardOptions = remember(inputType, capitalizeKeyboard, imeAction) {
        KeyboardOptions(
            keyboardType = when (inputType) {
                DaxTextFieldType.Password -> KeyboardType.Password
                DaxTextFieldType.IpAddress -> KeyboardType.Decimal
                DaxTextFieldType.UrlMode -> KeyboardType.Uri
                else -> KeyboardType.Text
            },
            imeAction = imeAction,
            capitalization = if (capitalizeKeyboard) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            autoCorrectEnabled = inputType != DaxTextFieldType.Password,
        )
    }

    val visualTransformation = when {
        inputType == DaxTextFieldType.Password && !isPasswordVisible -> PasswordVisualTransformation()
        else -> VisualTransformation.None
    }

    val colors = daxTextFieldColors()

    val actualMinLines = when (inputType) {
        DaxTextFieldType.FormMode -> 3
        else -> minLines
    }

    val actualMaxLines = when (inputType) {
        DaxTextFieldType.SingleLine -> 1
        else -> Int.MAX_VALUE
    }

    val singleLine = inputType == DaxTextFieldType.SingleLine

    // Determine if we should show the end icon
    val shouldShowEndIcon = when {
        endIcon == null -> false
        clickable -> true
        !isEditable -> true
        else -> true
    }

    // Determine trailing icon
    val trailingIcon: @Composable (() -> Unit)? = when {
        inputType == DaxTextFieldType.Password -> {
            {
                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    enabled = enabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPasswordVisible) R.drawable.ic_eye_closed_24 else R.drawable.ic_eye_24,
                        ),
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = DuckDuckGoTheme.colors.text.secondary,
                    )
                }
            }
        }

        shouldShowEndIcon && endIcon != null -> {
            {
                IconButton(
                    onClick = { onEndIconClick?.invoke() },
                    enabled = enabled,
                ) {
                    Icon(
                        painter = painterResource(endIcon),
                        contentDescription = endIconContentDescription,
                        tint = DuckDuckGoTheme.colors.text.secondary,
                    )
                }
            }
        }

        else -> null
    }

    Box(
        modifier = modifier.alpha(if (enabled) ENABLED_OPACITY else DISABLED_OPACITY),
    ) {
        MaterialTheme(
            typography = Typography(
                bodySmall = DuckDuckGoTheme.typography.caption.asTextStyle,
                bodyLarge = DuckDuckGoTheme.typography.body1.asTextStyle,
            ),
        ) {
            OutlinedTextField(
                value = textFieldValueState,
                onValueChange = { newValue ->
                    textFieldValueState = newValue
                    onValueChange(newValue.text)
                },
                modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            val wasFocused = isFocused
                            isFocused = focusState.isFocused
                            if (isFocused != wasFocused) {
                                onFocusChanged?.invoke(isFocused)
                                if (isFocused && clickable) {
                                    onEndIconClick?.invoke()
                                }
                            }
                        }
                        .then(
                                if (clickable && !isEditable) {
                                    Modifier.clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                    ) {
                                        onEndIconClick?.invoke()
                                    }
                                } else {
                                    Modifier
                                },
                        ),
                enabled = enabled && isEditable && !clickable,
                readOnly = !isEditable || clickable,
                label = if (hint.isNotEmpty()) {
                    {
                        MaterialTheme(
                            typography = Typography(
                                bodySmall = DuckDuckGoTheme.typography.caption.asTextStyle,
                                bodyLarge = DuckDuckGoTheme.typography.body1.asTextStyle,
                            ),
                        ) {
                            Text(
                                text = hint,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                } else null,
                textStyle = DuckDuckGoTheme.typography.body1.asTextStyle,
                trailingIcon = trailingIcon,
                isError = error != null,
                shape = DuckDuckGoTheme.shapes.small,
                supportingText = if (error != null) {
                    {
                        Text(
                            text = error,
                            style = DuckDuckGoTheme.typography.body1.asTextStyle,
                        )
                    }
                } else null,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                singleLine = singleLine,
                minLines = actualMinLines,
                maxLines = actualMaxLines,
                colors = colors,
            )
        }
    }
}

/**
 * Enum representing the different types of text field input modes.
 */
enum class DaxTextFieldType {
    /**
     * Multi-line text input
     */
    MultiLine,

    /**
     * Single-line text input with text truncation
     */
    SingleLine,

    /**
     * Password input with show/hide toggle
     */
    Password,

    /**
     * Form mode with minimum 3 lines
     */
    FormMode,

    /**
     * IP address input (numeric with dots)
     */
    IpAddress,

    /**
     * URL input mode
     */
    UrlMode,
}

@Composable
private fun daxTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = DuckDuckGoTheme.colors.text.primary,
        unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
        disabledTextColor = DuckDuckGoTheme.colors.text.disabled,
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
        disabledLabelColor = DuckDuckGoTheme.colors.text.disabled,
        errorLabelColor = DuckDuckGoTheme.colors.destructive,
        focusedTrailingIconColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedTrailingIconColor = DuckDuckGoTheme.colors.text.secondary,
        disabledTrailingIconColor = DuckDuckGoTheme.colors.text.disabled,
        errorTrailingIconColor = DuckDuckGoTheme.colors.text.secondary,
        focusedSupportingTextColor = DuckDuckGoTheme.colors.destructive,
        unfocusedSupportingTextColor = DuckDuckGoTheme.colors.destructive,
        disabledSupportingTextColor = DuckDuckGoTheme.colors.text.disabled,
        errorSupportingTextColor = DuckDuckGoTheme.colors.destructive,
        cursorColor = DuckDuckGoTheme.colors.accentBlue,
        errorCursorColor = DuckDuckGoTheme.colors.destructive,
        selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
            handleColor = DuckDuckGoTheme.colors.accentBlue,
            backgroundColor = DuckDuckGoTheme.colors.accentBlue.copy(alpha = 0.4f),
        ),
        focusedLeadingIconColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedLeadingIconColor = DuckDuckGoTheme.colors.text.secondary,
        disabledLeadingIconColor = DuckDuckGoTheme.colors.text.disabled,
        errorLeadingIconColor = DuckDuckGoTheme.colors.text.secondary,
        focusedPrefixColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedPrefixColor = DuckDuckGoTheme.colors.text.secondary,
        disabledPrefixColor = DuckDuckGoTheme.colors.text.disabled,
        errorPrefixColor = DuckDuckGoTheme.colors.text.secondary,
        focusedSuffixColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedSuffixColor = DuckDuckGoTheme.colors.text.secondary,
        disabledSuffixColor = DuckDuckGoTheme.colors.text.disabled,
        errorSuffixColor = DuckDuckGoTheme.colors.text.secondary,
        focusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        disabledPlaceholderColor = DuckDuckGoTheme.colors.text.disabled,
        errorPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
    )
}
