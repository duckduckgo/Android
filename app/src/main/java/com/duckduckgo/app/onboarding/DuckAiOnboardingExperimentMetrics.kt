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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckAiOnboardingExperimentMetrics @Inject constructor(
    private val toggles: ExtendedOnboardingFeatureToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        val toggle = toggles.onboardingDuckAiExperimentMay26()
        val conversionWindow = listOf(
            ConversionWindow(lowerWindow = 0, upperWindow = 0),
            ConversionWindow(lowerWindow = 0, upperWindow = 7),
        )

        return listOf(
            MetricsPixel(metric = METRIC_AICHAT_TYPE, value = VALUE_CUSTOM, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_AICHAT_TYPE, value = VALUE_OPTION_1, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_AICHAT_TYPE, value = VALUE_OPTION_2, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_AICHAT_TYPE, value = VALUE_OPTION_3, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SEARCH_TYPE, value = VALUE_CUSTOM, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SEARCH_TYPE, value = VALUE_OPTION_1, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SEARCH_TYPE, value = VALUE_OPTION_2, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SEARCH_TYPE, value = VALUE_OPTION_3, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SCREEN_IMPRESSION, value = VALUE_FIRE_DIALOG, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_SCREEN_IMPRESSION, value = VALUE_FINAL_DIALOG, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_CTA_PRESSED, value = VALUE_FIRE_BUTTON_PRESSED, toggle = toggle, conversionWindow = conversionWindow),
            MetricsPixel(metric = METRIC_CTA_PRESSED, value = VALUE_FINAL_DIALOG, toggle = toggle, conversionWindow = conversionWindow),
        )
    }

    /** @param optionIndex 1, 2 or 3 for the preset options; null for the custom option. */
    suspend fun fireAiChatType(optionIndex: Int?) = fireOptionType(METRIC_AICHAT_TYPE, optionIndex)

    /** @param optionIndex 1, 2 or 3 for the preset options; null for the custom option. */
    suspend fun fireSearchType(optionIndex: Int?) = fireOptionType(METRIC_SEARCH_TYPE, optionIndex)

    suspend fun fireFireDialogImpression() = fire(METRIC_SCREEN_IMPRESSION, VALUE_FIRE_DIALOG)

    suspend fun fireFinalDialogImpression() = fire(METRIC_SCREEN_IMPRESSION, VALUE_FINAL_DIALOG)

    suspend fun fireFireButtonPressed() = fire(METRIC_CTA_PRESSED, VALUE_FIRE_BUTTON_PRESSED)

    suspend fun fireFinalDialogPressed() = fire(METRIC_CTA_PRESSED, VALUE_FINAL_DIALOG)

    private suspend fun fireOptionType(metricName: String, optionIndex: Int?) {
        val value = when (optionIndex) {
            null -> VALUE_CUSTOM
            1 -> VALUE_OPTION_1
            2 -> VALUE_OPTION_2
            3 -> VALUE_OPTION_3
            else -> return
        }
        fire(metricName, value)
    }

    private suspend fun fire(metric: String, value: String) {
        getMetrics().firstOrNull { it.metric == metric && it.value == value }?.send()
    }

    companion object {
        private const val METRIC_AICHAT_TYPE = "aichat_type"
        private const val METRIC_SEARCH_TYPE = "search_type"
        private const val METRIC_SCREEN_IMPRESSION = "screen-impression"
        private const val METRIC_CTA_PRESSED = "cta-pressed"

        private const val VALUE_CUSTOM = "custom"
        private const val VALUE_OPTION_1 = "option1"
        private const val VALUE_OPTION_2 = "option2"
        private const val VALUE_OPTION_3 = "option3"
        private const val VALUE_FIRE_DIALOG = "fire-dialog"
        private const val VALUE_FINAL_DIALOG = "final-dialog"
        private const val VALUE_FIRE_BUTTON_PRESSED = "fire-button-pressed"
    }
}
