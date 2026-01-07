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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Text field component for the DuckDuckGo design system for entering passwords and other sensitive information.
 * It's a single line text field that obscures the input by default, with an option to toggle visibility.
 *
 * @param state The state of the text field that is used to read and write the text and selection.
 * @param modifier Optional [Modifier] for this text field. Can be used request focus via [Modifier.focusRequester] for example.
 * @param label Optional label/hint text to display inside the text field when it's empty or above the text field when it has text or is focused.
 * @param inputMode Input mode for the text field, such as editable, read-only or disabled. See [DaxTextFieldInputMode] for details.
 * @param error Optional error message to display below the text field. If provided, the text field will be styled to indicate an error.
 * @param keyboardOptions Software keyboard options that contains configuration such as [KeyboardType] and [ImeAction].
 * See [DaxTextFieldDefaults.TextKeyboardOptions], [DaxTextFieldDefaults.IpAddressKeyboardOptions] and
 * [DaxTextFieldDefaults.UrlKeyboardOptions] for examples.
 * @param interactionSource Optional interaction source for observing and emitting interaction events.
 * You can use this to observe focus, pressed, hover and drag events.
 * @param trailingIcon Optional trailing icon composable to display at the end of the text field.
 * Use [DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon] to create the icon.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1212213756433276?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=auto&node-id=3202-5150
 */
@Composable
fun DaxSecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = null,
    inputMode: DaxTextFieldInputMode = DaxTextFieldInputMode.Editable,
    error: String? = null,
    keyboardOptions: KeyboardOptions = DaxTextFieldDefaults.PasswordKeyboardOptions,
    interactionSource: MutableInteractionSource? = null,
    trailingIcon: (@Composable DaxTextFieldTrailingIconScope.() -> Unit)? = null,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    DaxSecureTextField(
        state = state,
        isPasswordVisible = isPasswordVisible,
        onShowHidePasswordIconClick = {
            isPasswordVisible = !isPasswordVisible
        },
        modifier = modifier,
        label = label,
        inputMode = inputMode,
        error = error,
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        trailingIcon = trailingIcon,
    )
}

/**
 * Text field component for the DuckDuckGo design system for entering passwords and other sensitive information.
 * It's a single line text field that obscures the input by default, with an option to toggle visibility.
 *
 * @param state The state of the text field that is used to read and write the text and selection.
 * @param isPasswordVisible Boolean flag indicating whether the password is currently visible or obscured.
 * You should manage this state and update it accordingly when [onShowHidePasswordIconClick] is called.
 * @param onShowHidePasswordIconClick Callback for when the show/hide password icon is clicked by the user.
 * You should update the [isPasswordVisible] state accordingly.
 * @param modifier Optional [Modifier] for this text field. Can be used request focus via [Modifier.focusRequester] for example.
 * @param label Optional label/hint text to display inside the text field when it's empty or above the text field when it has text or is focused.
 * @param inputMode Input mode for the text field, such as editable, read-only or disabled. See [DaxTextFieldInputMode] for details.
 * @param error Optional error message to display below the text field. If provided, the text field will be styled to indicate an error.
 * @param keyboardOptions Software keyboard options that contains configuration such as [KeyboardType] and [ImeAction].
 * See [DaxTextFieldDefaults.TextKeyboardOptions], [DaxTextFieldDefaults.IpAddressKeyboardOptions] and
 * [DaxTextFieldDefaults.UrlKeyboardOptions] for examples.
 * @param interactionSource Optional interaction source for observing and emitting interaction events.
 * You can use this to observe focus, pressed, hover and drag events.
 * @param trailingIcon Optional trailing icon composable to display at the end of the text field.
 * Use [DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon] to create the icon.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1212213756433276?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=auto&node-id=3202-5150
 */
@Composable
internal fun DaxSecureTextField(
    state: TextFieldState,
    isPasswordVisible: Boolean,
    onShowHidePasswordIconClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    inputMode: DaxTextFieldInputMode = DaxTextFieldInputMode.Editable,
    error: String? = null,
    keyboardOptions: KeyboardOptions = DaxTextFieldDefaults.PasswordKeyboardOptions,
    interactionSource: MutableInteractionSource? = null,
    trailingIcon: (@Composable DaxTextFieldTrailingIconScope.() -> Unit)? = null,
) {
    // needed by the OutlinedTextField container
    val internalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val daxTextFieldColors = daxTextFieldColors()

    // combine the password visibility toggle icon with any provided trailing icon
    val trailingIconCombined: @Composable (() -> Unit)? = {
        Row {
            DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                painter = painterResource(
                    if (isPasswordVisible) {
                        R.drawable.ic_eye_closed_24
                    } else {
                        R.drawable.ic_eye_24
                    },
                ),
                contentDescription = null,
                onClick = onShowHidePasswordIconClick,
                enabled = inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly,
            )

            trailingIcon?.let {
                DaxTextFieldTrailingIconScope.it()
            }
        }
    }

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
        CompositionLocalProvider(LocalTextSelectionColors provides daxTextFieldColors.textSelectionColors) {
            // need to use BasicSecureTextField over OutlinedSecureTextField as the latter does not support readOnly mode
            BasicSecureTextField(
                state = state,
                modifier =
                modifier
                    .alpha(
                        if (inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly) {
                            DaxTextFieldDefaults.ALPHA_ENABLED
                        } else {
                            DaxTextFieldDefaults.ALPHA_DISABLED
                        },
                    )
                    .then(
                        if (label != null) {
                            Modifier
                                // Merge semantics at the beginning of the modifier chain to ensure
                                // padding is considered part of the text field.
                                .semantics(mergeDescendants = true) {}
                                .padding(top = minimizedLabelHalfHeight())
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (!error.isNullOrBlank()) {
                            Modifier.semantics { error(error) }
                        } else {
                            Modifier
                        },
                    )
                    .defaultMinSize(
                        minWidth = OutlinedTextFieldDefaults.MinWidth,
                        minHeight = OutlinedTextFieldDefaults.MinHeight,
                    ),
                enabled = inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly,
                readOnly = inputMode == DaxTextFieldInputMode.ReadOnly || inputMode == DaxTextFieldInputMode.Disabled,
                textStyle = DuckDuckGoTheme.typography.body1.asTextStyle.copy(
                    color = DuckDuckGoTheme.textColors.primary,
                ),
                cursorBrush = SolidColor(daxTextFieldColors.cursorColor),
                keyboardOptions = keyboardOptions,
                interactionSource = internalInteractionSource,
                textObfuscationMode = if (isPasswordVisible) {
                    TextObfuscationMode.Visible
                } else {
                    TextObfuscationMode.Hidden
                },
                textObfuscationCharacter = DefaultObfuscationCharacter,
                decorator =
                OutlinedTextFieldDefaults.decorator(
                    state = state,
                    enabled = inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = null,
                    interactionSource = internalInteractionSource,
                    labelPosition = TextFieldLabelPosition.Attached(),
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
                    trailingIcon = trailingIconCombined,
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
                    isError = !error.isNullOrBlank(),
                    colors = daxTextFieldColors,
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = inputMode == DaxTextFieldInputMode.Editable || inputMode == DaxTextFieldInputMode.ReadOnly,
                            isError = !error.isNullOrBlank(),
                            interactionSource = internalInteractionSource,
                            colors = daxTextFieldColors,
                            shape = DuckDuckGoTheme.shapes.small,
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun minimizedLabelHalfHeight(): Dp {
    val compositionLocalValue = MaterialTheme.typography.bodySmall.lineHeight
    val fallbackValue = 16.sp
    val value = if (compositionLocalValue.isSp) compositionLocalValue else fallbackValue
    return with(LocalDensity.current) { value.toDp() / 2 }
}

private const val DefaultObfuscationCharacter: Char = '\u2022'

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldEmptyPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(),
            label = "Enter password",
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
        )
    }
}

@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxSecureTextFieldWithPlainTextPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            label = "Enter password",
            isPasswordVisible = true,
            onShowHidePasswordIconClick = {},
        )
    }
}

@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxSecureTextFieldWithObscureTextPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            label = "Enter password",
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldNoLabelPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldEditablePreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            inputMode = DaxTextFieldInputMode.Editable,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldDisabledPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            inputMode = DaxTextFieldInputMode.Disabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldNonEditablePreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Read only password",
            inputMode = DaxTextFieldInputMode.ReadOnly,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldWithErrorPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "weak"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            error = "Password must be at least 8 characters",
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldWithTrailingIconPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "SecurePassword123"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                    onClick = {},
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldEmptyWithTrailingIconPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                    onClick = { },
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSecureTextFieldErrorWithTrailingIconPreview() {
    DaxSecureTextFieldPreviewBox {
        DaxSecureTextField(
            state = TextFieldState(initialText = "weak"),
            isPasswordVisible = false,
            onShowHidePasswordIconClick = {},
            label = "Enter password",
            error = "Password must contain uppercase, lowercase, and numbers",
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy",
                    onClick = {},
                )
            },
        )
    }
}

@Composable
private fun DaxSecureTextFieldPreviewBox(
    content: @Composable () -> Unit,
) {
    DuckDuckGoTheme {
        PreviewBox {
            content()
        }
    }
}
