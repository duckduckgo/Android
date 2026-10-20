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

package com.duckduckgo.common.ui.compose.theme

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Temporary onboarding-only colour overlay. This is a for-now choice while onboarding Compose
 * theming settles; it intentionally overlays, rather than replacing, [DuckDuckGoTheme].
 */
@Immutable
internal data class OnboardingColors(
    val surfaceTertiary: Color,
    val accentAltPrimary: Color,
    val accentPrimary: Color,
    val textPrimary: Color,
    val iconsPrimary: Color,
    val bubbleShadow: Color,
)

@SuppressLint("ComposeCompositionLocalUsage")
internal val LocalOnboardingColors = compositionLocalOf<OnboardingColors> {
    error("No OnboardingTheme found — wrap the content in OnboardingTheme { }")
}

/**
 * Provides the temporary onboarding colour overlay inside [DuckDuckGoTheme].
 * Call this provider around every onboarding dialog; the dialog reads its colours from the
 * onboarding composition local, and omitting the provider causes that local to throw.
 *
 * @param content Content rendered with the onboarding colour overlay installed.
 */
@Composable
fun OnboardingTheme(content: @Composable () -> Unit) {
    val colors = if (DuckDuckGoTheme.colors.isDark) darkOnboardingColors else lightOnboardingColors()

    CompositionLocalProvider(
        LocalOnboardingColors provides colors,
        content = content,
    )
}

private val darkOnboardingColors = OnboardingColors(
    surfaceTertiary = Color(0xFF011D34),
    accentAltPrimary = Color(0xFF133E7C),
    accentPrimary = Color(0xFF75B6EB),
    textPrimary = White,
    iconsPrimary = Color(0xD6FBFAF9),
    bubbleShadow = Color(0x08000000),
)

@Composable
private fun lightOnboardingColors() = OnboardingColors(
    surfaceTertiary = White,
    accentAltPrimary = Pondwater20,
    accentPrimary = Pondwater50,
    textPrimary = Color(0xF5242323),
    iconsPrimary = Color(0xD6242323),
    bubbleShadow = Color(0x08000000),
)
