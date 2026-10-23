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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.duckduckgo.common.ui.compose.onboarding

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.duckduckgo.common.ui.compose.button.DaxButtonColors
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@Composable
fun DaxOnboardingSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = DuckDuckGoTheme.colors.isDark
    val container = colorResource(if (dark) R.color.white4 else R.color.black6)
    val content = colorResource(if (dark) R.color.white else R.color.black96)
    val pressed = colorResource(if (dark) R.color.white8 else R.color.black12)
    ProvideDuckDuckGoTheme(
        colors = DuckDuckGoTheme.colors,
        shapes = DuckDuckGoTheme.shapes.copy(medium = CircleShape),
        typography = DuckDuckGoTheme.typography.copy(button = DuckDuckGoTheme.typography.onboardingButton),
    ) {
        DaxOnboardingButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = DaxButtonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container.copy(alpha = container.alpha * 0.38f),
                disabledContentColor = content.copy(alpha = content.alpha * 0.38f),
            ),
            rippleConfiguration = remember(pressed) { RippleConfiguration(color = pressed) },
        )
    }
}
