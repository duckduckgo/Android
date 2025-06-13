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

package com.duckduckgo.app.widget.experiment

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.widget.experiment.PostCtaExperienceToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
interface PostCtaExperienceToggles {

    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun postCtaExperienceExperimentJun25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"), // Search and Favorites widget prompt
        VARIANT_SIMPLE_SEARCH_WIDGET_PROMPT("simpleSearchWidgetPrompt"), // Simple Search widget prompt
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "postCtaExperience"
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class PostCtaExperiencePixelsPlugin @Inject constructor(
    private val toggles: PostCtaExperienceToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = METRIC_SETTINGS_WIDGET_DISPLAY,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_SETTINGS_WIDGET_ADD,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_SETTINGS_WIDGET_DISMISS,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH_3X,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH_5X,
                value = "1",
                toggle = toggles.postCtaExperienceExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                ),
            ),
        )
    }

    suspend fun getSettingsWidgetDisplayMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SETTINGS_WIDGET_DISPLAY }
    }

    suspend fun getSettingsWidgetAddMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SETTINGS_WIDGET_ADD }
    }

    suspend fun getSettingsWidgetDismissMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SETTINGS_WIDGET_DISMISS }
    }

    suspend fun getWidgetSearchMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_WIDGET_SEARCH }
    }

    suspend fun getWidgetSearch3xMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_WIDGET_SEARCH_3X }
    }

    suspend fun getWidgetSearch5xMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_WIDGET_SEARCH_5X }
    }

    companion object {
        internal const val METRIC_SETTINGS_WIDGET_DISPLAY = "settingsWidgetDisplay"
        internal const val METRIC_SETTINGS_WIDGET_ADD = "settingsWidgetAdd"
        internal const val METRIC_SETTINGS_WIDGET_DISMISS = "settingsWidgetDismiss"
        internal const val METRIC_WIDGET_SEARCH = "widgetSearch"
        internal const val METRIC_WIDGET_SEARCH_3X = "widgetSearch3x"
        internal const val METRIC_WIDGET_SEARCH_5X = "widgetSearch5x"
    }
}
