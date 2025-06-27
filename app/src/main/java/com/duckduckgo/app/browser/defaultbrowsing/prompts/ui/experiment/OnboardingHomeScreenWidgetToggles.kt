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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.FALSE
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
interface OnboardingHomeScreenWidgetToggles {

    @DefaultValue(FALSE)
    fun self(): Toggle

    @DefaultValue(FALSE)
    fun onboardingHomeScreenWidgetExperimentJun25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"), // current bottom sheet prompt
        VARIANT_ONBOARDING_HOME_SCREEN_WIDGET_PROMPT("experimentalOnboardingHomeScreenWidgetPrompt"), // new bottom sheet prompt
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "onboardingHomeScreenWidget"
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class OnboardingHomeScreenWidgetPixelsPlugin @Inject constructor(
    private val toggles: OnboardingHomeScreenWidgetToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = METRIC_ONBOARDING_WIDGET_DISPLAY,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_ONBOARDING_WIDGET_ADD,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_ONBOARDING_WIDGET_DISMISS,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                    ConversionWindow(lowerWindow = 8, upperWindow = 14),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH_3X,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                ),
            ),
            MetricsPixel(
                metric = METRIC_WIDGET_SEARCH_5X,
                value = "1",
                toggle = toggles.onboardingHomeScreenWidgetExperimentJun25(),
                conversionWindow = listOf(
                    ConversionWindow(lowerWindow = 5, upperWindow = 7),
                ),
            ),
        )
    }

    suspend fun getOnboardingWidgetDisplayMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_ONBOARDING_WIDGET_DISPLAY }
    }

    suspend fun getOnboardingWidgetAddMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_ONBOARDING_WIDGET_ADD }
    }

    suspend fun getOnboardingWidgetDismissMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_ONBOARDING_WIDGET_DISMISS }
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
        internal const val METRIC_ONBOARDING_WIDGET_DISPLAY = "onboardingWidgetDisplay"
        internal const val METRIC_ONBOARDING_WIDGET_ADD = "onboardingWidgetAdd"
        internal const val METRIC_ONBOARDING_WIDGET_DISMISS = "onboardingWidgetDismiss"
        internal const val METRIC_WIDGET_SEARCH = "widgetSearch"
        internal const val METRIC_WIDGET_SEARCH_3X = "widgetSearch3x"
        internal const val METRIC_WIDGET_SEARCH_5X = "widgetSearch5x"
    }
}
