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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultBrowserPromptsExperimentMetrics @Inject constructor(
    private val defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles,
) : MetricsPixelPlugin {
    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = METRIC_DEFAULT_SET,
                value = METRIC_VALUE_STAGE_1,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
            MetricsPixel(
                metric = METRIC_DEFAULT_SET,
                value = METRIC_VALUE_STAGE_2,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
            MetricsPixel(
                metric = METRIC_DEFAULT_SET_VIA_CTA,
                value = METRIC_VALUE_DIALOG,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
            MetricsPixel(
                metric = METRIC_DEFAULT_SET_VIA_CTA,
                value = METRIC_VALUE_MENU,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
            MetricsPixel(
                metric = METRIC_STAGE_IMPRESSION,
                value = METRIC_VALUE_STAGE_1,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
            MetricsPixel(
                metric = METRIC_STAGE_IMPRESSION,
                value = METRIC_VALUE_STAGE_2,
                toggle = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
                conversionWindow = (20..60 step 20).map { ConversionWindow(lowerWindow = 1, upperWindow = it) },
            ),
        )
    }

    suspend fun getDefaultSetForStage1(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DEFAULT_SET && it.value == METRIC_VALUE_STAGE_1 }
    }

    suspend fun getDefaultSetForStage2(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DEFAULT_SET && it.value == METRIC_VALUE_STAGE_2 }
    }

    suspend fun getDefaultSetViaDialog(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DEFAULT_SET_VIA_CTA && it.value == METRIC_VALUE_DIALOG }
    }

    suspend fun getDefaultSetViaMenu(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_DEFAULT_SET_VIA_CTA && it.value == METRIC_VALUE_MENU }
    }

    suspend fun getStageImpressionForStage1(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_STAGE_IMPRESSION && it.value == METRIC_VALUE_STAGE_1 }
    }

    suspend fun getStageImpressionForStage2(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_STAGE_IMPRESSION && it.value == METRIC_VALUE_STAGE_2 }
    }

    companion object {
        const val METRIC_DEFAULT_SET = "defaultSet"
        const val METRIC_DEFAULT_SET_VIA_CTA = "defaultSetViaCta"
        const val METRIC_STAGE_IMPRESSION = "stageImpression"

        const val METRIC_VALUE_STAGE_1 = "stage_1"
        const val METRIC_VALUE_STAGE_2 = "stage_2"
        const val METRIC_VALUE_DIALOG = "dialog"
        const val METRIC_VALUE_MENU = "menu"
    }
}
