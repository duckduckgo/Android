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

package com.duckduckgo.app.onboarding.ui.page.welcome

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButton
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme

// TODO: Replace with the design system's updated secondary button once it's built.
@Composable
internal fun DaxOnboardingSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ProvideDuckDuckGoTheme(
        colors = DuckDuckGoTheme.colors,
        shapes = DuckDuckGoTheme.shapes.copy(medium = CircleShape),
        typography = DuckDuckGoTheme.typography.copy(button = DuckDuckGoTheme.typography.onboardingButton),
    ) {
        DaxSecondaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
    }
}
