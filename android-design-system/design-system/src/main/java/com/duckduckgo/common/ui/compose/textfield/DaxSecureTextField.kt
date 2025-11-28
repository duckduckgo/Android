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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R

/**
 * Text field component for the DuckDuckGo design system for entering passwords and other sensitive information.
 * It's a single line text field that obscures the input by default, with an option to toggle visibility.
 *
 * @param state The state of the text field that is used to read and write the text and selection.
 * @param modifier Optional [Modifier] for this text field. Can be used request focus via [Modifier.focusRequester] for example.
 * @param hint Optional hint text to display inside the text field when it's empty or above the text field when it has text or is focused.
 * @param enabled Whether the interaction with the text field is enabled or disabled.
 * @param editable Whether the text field is editable by the user or read-only.
 * @param clickable Whether the text field is clickable, triggering the [onClick] callback when focused.
 * @param error Optional error message to display below the text field. If provided, the text field will be styled to indicate an error.
 * @param trailingIcon Optional [DaxTextFieldTrailingIcon] that will be displayed at the end of the text field.
 * @param keyboardOptions Software keyboard options that contains configuration such as [KeyboardType] and [ImeAction].
 * @param onClick Optional callback that is triggered when the text field is [clickable] or when [trailingIcon] is clicked.
 * @param onFocusChanged Optional callback that is triggered when the focus state of the text field changes.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1212213756433276?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=auto&node-id=3202-5150
 */
@Composable
fun DaxSecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    editable: Boolean = true,
    clickable: Boolean = false,
    error: String? = null,
    trailingIcon: DaxTextFieldTrailingIcon? = null,
    keyboardOptions: KeyboardOptions = DaxTextFieldDefaults.PasswordKeyboardOptions,
    onClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    // combine the password visibility toggle icon with any provided trailing icon
    val trailingIconCombined: @Composable (() -> Unit)? = {
        Row {
            IconButton(
                onClick = { isPasswordVisible = !isPasswordVisible },
                enabled = enabled,
            ) {
                Icon(
                    painter = painterResource(
                        if (isPasswordVisible) {
                            R.drawable.ic_eye_closed_24
                        } else {
                            R.drawable.ic_eye_24
                        },
                    ),
                    contentDescription = null,
                    tint = DuckDuckGoTheme.colors.iconPrimary,
                )
            }

            trailingIcon?.ToIcon(
                onClick = onClick,
                enabled = enabled,
            )
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
        OutlinedSecureTextField(
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
            trailingIcon = trailingIconCombined,
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
            textObfuscationMode = if (isPasswordVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.Hidden
            },
            colors = daxTextFieldColors(),
        )
    }
}
