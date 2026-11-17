/*
 * Copyright (c) 2026 DuckDuckGo
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.tools.PreviewBox

object DaxSecureTextFieldScreenshot : ScreenshotCase(name = "DaxSecureTextField") {
    @Composable
    override fun Content() {
        PreviewBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DaxSecureTextField(
                    state = TextFieldState(),
                    label = "Empty",
                    isPasswordVisible = false,
                    onShowHidePasswordIconClick = {},
                )
                DaxSecureTextField(
                    state = TextFieldState(initialText = "SecurePassword123"),
                    label = "Obscured",
                    isPasswordVisible = false,
                    onShowHidePasswordIconClick = {},
                )
                DaxSecureTextField(
                    state = TextFieldState(initialText = "SecurePassword123"),
                    label = "Visible",
                    isPasswordVisible = true,
                    onShowHidePasswordIconClick = {},
                )
                DaxSecureTextField(
                    state = TextFieldState(initialText = "weak"),
                    label = "With error",
                    isPasswordVisible = false,
                    onShowHidePasswordIconClick = {},
                    error = "Password must be at least 8 characters",
                )
                DaxSecureTextField(
                    state = TextFieldState(initialText = "SecurePassword123"),
                    label = "Disabled",
                    isPasswordVisible = false,
                    onShowHidePasswordIconClick = {},
                    inputMode = DaxTextFieldInputMode.Disabled,
                )
            }
        }
    }
}
