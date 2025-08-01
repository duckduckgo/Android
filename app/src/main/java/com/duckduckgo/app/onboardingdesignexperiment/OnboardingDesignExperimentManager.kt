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

package com.duckduckgo.app.onboardingdesignexperiment

import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BB
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BUCK
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.MODIFIED_CONTROL
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface OnboardingDesignExperimentManager {
    suspend fun isAnyExperimentEnrolledAndEnabled(): Boolean
    suspend fun isBuckEnrolledAndEnabled(): Boolean
    suspend fun isBbEnrolledAndEnabled(): Boolean
    suspend fun isModifiedControlEnrolledAndEnabled(): Boolean
    suspend fun fireIntroScreenDisplayedPixel()
    suspend fun fireComparisonScreenDisplayedPixel()
    suspend fun fireChooseBrowserPixel()
    suspend fun fireSetDefaultRatePixel()
    suspend fun fireSetAddressBarDisplayedPixel()
    suspend fun fireAddressBarSetTopPixel()
    suspend fun fireAddressBarSetBottomPixel()
    suspend fun fireTryASearchDisplayedPixel()
    suspend fun fireFirstSearchSuggestionPixel()
    suspend fun fireSecondSearchSuggestionPixel()
    suspend fun fireThirdSearchSuggestionPixel()
    suspend fun fireSearchOrNavCustomPixel()
    suspend fun fireMessageOnSerpDisplayedPixel()
    suspend fun fireVisitSitePromptDisplayedAdjacentPixel()
    suspend fun fireVisitSitePromptDisplayedNewTabPixel()
    suspend fun fireFirstSiteSuggestionPixel()
    suspend fun fireSecondSiteSuggestionPixel()
    suspend fun fireThirdSiteSuggestionPixel()
    suspend fun fireTrackersBlockedMessageDisplayedPixel()
    suspend fun fireNoTrackersMessageDisplayedPixel()
    suspend fun fireTrackerNetworkMessageDisplayedPixel()
    suspend fun firePrivacyDashClickedFromOnboardingPixel()
    suspend fun fireFireButtonPromptDisplayedPixel()
    suspend fun fireFireButtonClickedFromOnboardingPixel()
    suspend fun fireFinalOnboardingScreenDisplayedPixel()
    suspend fun fireSecondSiteVisitPixel()
    suspend fun fireSecondSerpVisitPixel()
}

@ContributesBinding(AppScope::class)
class RealOnboardingDesignExperimentManager @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    private val onboardingExperimentMetricsPixelPlugin: OnboardingExperimentMetricsPixelPlugin,
    private val onboardingDesignExperimentCountDataStore: OnboardingDesignExperimentCountDataStore,
    private val pixel: Pixel,
) : OnboardingDesignExperimentManager {

    override suspend fun isAnyExperimentEnrolledAndEnabled() = isBuckEnrolledAndEnabled() || isBbEnrolledAndEnabled()

    override suspend fun isBuckEnrolledAndEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().isEnrolledAndEnabled(BUCK)
        }
    }

    override suspend fun isBbEnrolledAndEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().isEnrolledAndEnabled(BB)
        }
    }

    override suspend fun isModifiedControlEnrolledAndEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().isEnrolledAndEnabled(MODIFIED_CONTROL)
        }
    }

    override suspend fun fireIntroScreenDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getIntroScreenDisplayedMetric()?.fire()
    }

    override suspend fun fireComparisonScreenDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getComparisonScreenDisplayedMetric()?.fire()
    }

    override suspend fun fireChooseBrowserPixel() {
        onboardingExperimentMetricsPixelPlugin.getChooseBrowserMetric()?.fire()
    }

    override suspend fun fireSetDefaultRatePixel() {
        onboardingExperimentMetricsPixelPlugin.getSetDefaultRateMetric()?.fire()
    }

    override suspend fun fireSetAddressBarDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getSetAddressBarDisplayedMetric()?.fire()
    }

    override suspend fun fireAddressBarSetTopPixel() {
        onboardingExperimentMetricsPixelPlugin.getAddressBarSetTopMetric()?.fire()
    }

    override suspend fun fireAddressBarSetBottomPixel() {
        onboardingExperimentMetricsPixelPlugin.getAddressBarSetBottomMetric()?.fire()
    }

    override suspend fun fireTryASearchDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTryASearchDisplayedMetric()?.fire()
    }

    override suspend fun fireFirstSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getFirstSearchSuggestionMetric()?.fire()
    }

    override suspend fun fireSecondSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getSecondSearchSuggestionMetric()?.fire()
    }

    override suspend fun fireThirdSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getThirdSearchSuggestionMetric()?.fire()
    }

    override suspend fun fireSearchOrNavCustomPixel() {
        onboardingExperimentMetricsPixelPlugin.getSearchOrNavCustomMetric()?.fire()
    }

    override suspend fun fireMessageOnSerpDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getMessageOnSerpDisplayedMetric()?.fire()
    }

    override suspend fun fireVisitSitePromptDisplayedAdjacentPixel() {
        onboardingExperimentMetricsPixelPlugin.getVisitSitePromptDisplayedAdjacentMetric()?.fire()
    }

    override suspend fun fireVisitSitePromptDisplayedNewTabPixel() {
        onboardingExperimentMetricsPixelPlugin.getVisitSitePromptDisplayedNewTabMetric()?.fire()
    }

    override suspend fun fireFirstSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getFirstSiteSuggestionMetric()?.fire()
    }

    override suspend fun fireSecondSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getSecondSiteSuggestionMetric()?.fire()
    }

    override suspend fun fireThirdSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getThirdSiteSuggestionMetric()?.fire()
    }

    override suspend fun fireTrackersBlockedMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTrackersBlockedMessageDisplayedMetric()?.fire()
    }

    override suspend fun fireNoTrackersMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getNoTrackersMessageDisplayedMetric()?.fire()
    }

    override suspend fun fireTrackerNetworkMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTrackerNetworkMessageDisplayedMetric()?.fire()
    }

    override suspend fun firePrivacyDashClickedFromOnboardingPixel() {
        onboardingExperimentMetricsPixelPlugin.getPrivacyDashClickedFromOnboardingMetric()?.fire()
    }

    override suspend fun fireFireButtonPromptDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getFireButtonPromptDisplayedMetric()?.fire()
    }

    override suspend fun fireFireButtonClickedFromOnboardingPixel() {
        onboardingExperimentMetricsPixelPlugin.getFireButtonClickedFromOnboardingMetric()?.fire()
    }

    override suspend fun fireFinalOnboardingScreenDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getFinalOnboardingScreenDisplayedMetric()?.fire()
    }

    override suspend fun fireSecondSiteVisitPixel() {
        onboardingDesignExperimentCountDataStore.getSiteVisitCount().takeIf { it < 2 }?.let {
            onboardingDesignExperimentCountDataStore.increaseSiteVisitCount().takeIf { it == 2 }?.apply {
                onboardingExperimentMetricsPixelPlugin.getSecondSiteVisitMetric()?.fire()
            }
        }
    }

    override suspend fun fireSecondSerpVisitPixel() {
        onboardingDesignExperimentCountDataStore.getSerpVisitCount().takeIf { it < 2 }?.let {
            onboardingDesignExperimentCountDataStore.increaseSerpVisitCount().takeIf { it == 2 }?.apply {
                onboardingExperimentMetricsPixelPlugin.getSecondSearchVisitMetric()?.fire()
            }
        }
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
