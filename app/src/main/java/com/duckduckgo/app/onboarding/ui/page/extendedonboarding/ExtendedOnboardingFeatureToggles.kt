/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.extendedonboarding

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.Experiment
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "extendedOnboarding",
)
interface ExtendedOnboardingFeatureToggles {

    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun noBrowserCtas(): Toggle

    @Toggle.DefaultValue(false)
    fun privacyProCta(): Toggle

    @Toggle.DefaultValue(false)
    @Experiment
    fun highlights(): Toggle

    @Toggle.DefaultValue(false)
    fun testPrivacyProOnboardingCopyNov24(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        PROTECTION("protection"),
        DEAL("deal"),
        STEP("step"),
    }
}

internal suspend fun ExtendedOnboardingPixelsPlugin.testPrivacyProOnboardingShownMetricPixel(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "dialogShown" }
}

internal suspend fun ExtendedOnboardingPixelsPlugin.testPrivacyProOnboardingPrimaryButtonMetricPixel(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "primaryButtonSelected" }
}

internal suspend fun ExtendedOnboardingPixelsPlugin.testPrivacyProOnboardingSecondaryButtonMetricPixel(): MetricsPixel? {
    return this.getMetrics().firstOrNull { it.metric == "secondaryButtonSelected" }
}

@ContributesMultibinding(AppScope::class)
class ExtendedOnboardingPixelsPlugin @Inject constructor(private val toggle: ExtendedOnboardingFeatureToggles) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = "dialogShown",
                value = "1",
                toggle = toggle.testPrivacyProOnboardingCopyNov24(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 1)),
            ),
            MetricsPixel(
                metric = "primaryButtonSelected",
                value = "1",
                toggle = toggle.testPrivacyProOnboardingCopyNov24(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 1)),
            ),
            MetricsPixel(
                metric = "secondaryButtonSelected",
                value = "1",
                toggle = toggle.testPrivacyProOnboardingCopyNov24(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 1)),
            ),
        )
    }
}
