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

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import com.duckduckgo.common.ui.compose.button.DaxBrandButtonDefaults
import com.duckduckgo.common.ui.compose.button.DaxButton
import com.duckduckgo.common.ui.compose.button.DaxButtonColors
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R

@Composable
fun DaxOnboardingPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ProvideDuckDuckGoTheme(
        colors = DuckDuckGoTheme.colors,
        shapes = DuckDuckGoTheme.shapes.copy(medium = DaxBrandButtonDefaults.shape),
        typography = DuckDuckGoTheme.typography.copy(button = DuckDuckGoTheme.typography.onboardingButton),
    ) {
        DaxOnboardingButton(
            text = text,
            onClick = onClick,
            colors = DaxBrandButtonDefaults.colors(),
            rippleConfiguration = DaxBrandButtonDefaults.rippleConfiguration(),
            modifier = modifier,
            enabled = enabled,
        )
    }
}

@Composable
internal fun DaxOnboardingButton(
    text: String,
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = DuckDuckGoTheme.typography.onboardingButton.asTextStyle
    val minimum = with(density) { dimensionResource(R.dimen.rebrandButtonSmallHeight).roundToPx() }
    val inset = with(density) { dimensionResource(R.dimen.rebrandButtonSmallVerticalInset).roundToPx() }
    val padding = with(density) { dimensionResource(R.dimen.rebrandButtonSmallTopPadding).roundToPx() }
    val sidePadding = with(density) { dimensionResource(R.dimen.buttonSmallSidePadding).roundToPx() }
    DaxButton(
        text = text,
        onClick = onClick,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        enabled = enabled,
        textModifier = Modifier.fillMaxHeight().wrapContentHeight(Alignment.Vertical { size, space -> (space - size) / 2 }),
        modifier = modifier.layout { measurable, constraints ->
            val textWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - 2 * sidePadding).coerceAtLeast(0) else Constraints.Infinity
            val label = measurer.measure(text, style, constraints = Constraints(maxWidth = textWidth))
            val height = constraints.constrainHeight(maxOf(minimum, label.size.height + 2 * padding) - 2 * inset)
            val button = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(button.width, button.height) { button.place(0, 0) }
        },
    )
}
