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

package com.duckduckgo.common.ui.compose.onboarding

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
@Composable
internal fun DaxOnboardingDialogBodyText(
    text: AnnotatedString,
    style: DuckDuckGoTextStyle,
    textAlign: DaxOnboardingDialogTextAlign,
) {
    DaxText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = style,
        color = DaxOnboardingDialogDefaults.ContentColor,
        textAlign = textAlign.composeTextAlign,
    )
}

@Composable
internal fun OnboardingPreview(content: @Composable () -> Unit) {
    DuckDuckGoTheme {
        OnboardingTheme {
            PreviewBox(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}
