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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
/**
 * Interface defining feature toggles for the onboarding design experiment.
 * These toggles control specific features related to the onboarding process.
 */
interface OnboardingDesignExperimentToggles {

    /**
     * Toggle for enabling or disabling the "self" onboarding design experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onboardingDesignExperimentAug25(): Toggle

    enum class OnboardingDesignExperimentCohort(override val cohortName: String) : Toggle.State.CohortName {
        MODIFIED_CONTROL("modifiedControl"),
        BUCK("buck"),
        BB("bb"),
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "onboardingDesignExperiment"
    }
}

@ContributesMultibinding(AppScope::class)
class OnboardingExperimentMetricsPixelPlugin @Inject constructor(
    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        val toggle = onboardingDesignExperimentToggles.onboardingDesignExperimentAug25()
        val conversionWindow = listOf(
            ConversionWindow(lowerWindow = 0, upperWindow = 0),
            ConversionWindow(lowerWindow = 1, upperWindow = 1),
            ConversionWindow(lowerWindow = 0, upperWindow = 7),
            ConversionWindow(lowerWindow = 0, upperWindow = 14),
        )

        return listOf(
            MetricsPixel(
                metric = METRIC_INTRO_SCREEN_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_COMPARISON_SCREEN_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_CHOOSE_BROWSER,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SET_DEFAULT_RATE,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SET_ADDRESS_BAR_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_ADDRESS_BAR_SET_TOP,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_ADDRESS_BAR_SET_BOTTOM,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_TRY_A_SEARCH_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_FIRST_SEARCH_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SECOND_SEARCH_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_THIRD_SEARCH_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SEARCH_OR_NAV_CUSTOM,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_MESSAGE_ON_SERP_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_VISIT_SITE_PROMPT_DISPLAYED_ADJACENT,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_VISIT_SITE_PROMPT_DISPLAYED_NEW_TAB,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_FIRST_SITE_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SECOND_SITE_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_THIRD_SITE_SUGGESTION,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_TRACKERS_BLOCKED_MESSAGE_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_NO_TRACKERS_MESSAGE_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_TRACKER_NETWORK_MESSAGE_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_PRIVACY_DASH_CLICKED_FROM_ONBOARDING,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_FIRE_BUTTON_PROMPT_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_FIRE_BUTTON_CLICKED_FROM_ONBOARDING,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_FINAL_ONBOARDING_SCREEN_DISPLAYED,
                value = "1",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SECOND_SITE_VISIT,
                value = "2",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
            MetricsPixel(
                metric = METRIC_SECOND_SERP_VISIT,
                value = "2",
                toggle = toggle,
                conversionWindow = conversionWindow,
            ),
        )
    }

    suspend fun getIntroScreenDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_INTRO_SCREEN_DISPLAYED }
    }

    suspend fun getComparisonScreenDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_COMPARISON_SCREEN_DISPLAYED }
    }

    suspend fun getChooseBrowserMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_CHOOSE_BROWSER }
    }

    suspend fun getSetDefaultRateMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SET_DEFAULT_RATE }
    }

    suspend fun getSetAddressBarDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SET_ADDRESS_BAR_DISPLAYED }
    }

    suspend fun getAddressBarSetTopMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_ADDRESS_BAR_SET_TOP }
    }

    suspend fun getAddressBarSetBottomMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_ADDRESS_BAR_SET_BOTTOM }
    }

    suspend fun getTryASearchDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_TRY_A_SEARCH_DISPLAYED }
    }

    suspend fun getFirstSearchSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_FIRST_SEARCH_SUGGESTION }
    }

    suspend fun getSecondSearchSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SECOND_SEARCH_SUGGESTION }
    }

    suspend fun getThirdSearchSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_THIRD_SEARCH_SUGGESTION }
    }

    suspend fun getSearchOrNavCustomMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SEARCH_OR_NAV_CUSTOM }
    }

    suspend fun getMessageOnSerpDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_MESSAGE_ON_SERP_DISPLAYED }
    }

    suspend fun getVisitSitePromptDisplayedAdjacentMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_VISIT_SITE_PROMPT_DISPLAYED_ADJACENT }
    }

    suspend fun getVisitSitePromptDisplayedNewTabMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_VISIT_SITE_PROMPT_DISPLAYED_NEW_TAB }
    }

    suspend fun getFirstSiteSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_FIRST_SITE_SUGGESTION }
    }

    suspend fun getSecondSiteSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SECOND_SITE_SUGGESTION }
    }

    suspend fun getThirdSiteSuggestionMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_THIRD_SITE_SUGGESTION }
    }

    suspend fun getTrackersBlockedMessageDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_TRACKERS_BLOCKED_MESSAGE_DISPLAYED }
    }

    suspend fun getNoTrackersMessageDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_NO_TRACKERS_MESSAGE_DISPLAYED }
    }

    suspend fun getTrackerNetworkMessageDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_TRACKER_NETWORK_MESSAGE_DISPLAYED }
    }

    suspend fun getPrivacyDashClickedFromOnboardingMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_PRIVACY_DASH_CLICKED_FROM_ONBOARDING }
    }

    suspend fun getFireButtonPromptDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_FIRE_BUTTON_PROMPT_DISPLAYED }
    }

    suspend fun getFireButtonClickedFromOnboardingMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_FIRE_BUTTON_CLICKED_FROM_ONBOARDING }
    }

    suspend fun getFinalOnboardingScreenDisplayedMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_FINAL_ONBOARDING_SCREEN_DISPLAYED }
    }

    suspend fun getSecondSiteVisitMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SECOND_SITE_VISIT }
    }

    suspend fun getSecondSerpVisitMetric(): MetricsPixel? {
        return this.getMetrics().firstOrNull { it.metric == METRIC_SECOND_SERP_VISIT }
    }

    private companion object {
        const val METRIC_INTRO_SCREEN_DISPLAYED = "introScreenDisplayed"
        const val METRIC_COMPARISON_SCREEN_DISPLAYED = "comparisonScreenDisplayed"
        const val METRIC_CHOOSE_BROWSER = "chooseBrowser"
        const val METRIC_SET_DEFAULT_RATE = "setDefaultRate"
        const val METRIC_SET_ADDRESS_BAR_DISPLAYED = "setAddressBarDisplayed"
        const val METRIC_ADDRESS_BAR_SET_TOP = "addressBarSetTop"
        const val METRIC_ADDRESS_BAR_SET_BOTTOM = "addressBarSetBottom"
        const val METRIC_TRY_A_SEARCH_DISPLAYED = "tryASearchDisplayed"
        const val METRIC_FIRST_SEARCH_SUGGESTION = "firstSearchSuggestion"
        const val METRIC_SECOND_SEARCH_SUGGESTION = "secondSearchSuggestion"
        const val METRIC_THIRD_SEARCH_SUGGESTION = "thirdSearchSuggestion"
        const val METRIC_SEARCH_OR_NAV_CUSTOM = "searchOrNavCustom"
        const val METRIC_MESSAGE_ON_SERP_DISPLAYED = "messageOnSERPDisplayed"
        const val METRIC_VISIT_SITE_PROMPT_DISPLAYED_ADJACENT = "visitSitePromptDisplayedAdjacent"
        const val METRIC_VISIT_SITE_PROMPT_DISPLAYED_NEW_TAB = "visitSitePromptDisplayedNewTab"
        const val METRIC_FIRST_SITE_SUGGESTION = "firstSiteSuggestion"
        const val METRIC_SECOND_SITE_SUGGESTION = "secondSiteSuggestion"
        const val METRIC_THIRD_SITE_SUGGESTION = "thirdSiteSuggestion"
        const val METRIC_TRACKERS_BLOCKED_MESSAGE_DISPLAYED = "trackersBlockedMessageDisplayed"
        const val METRIC_NO_TRACKERS_MESSAGE_DISPLAYED = "noTrackersMessageDisplayed"
        const val METRIC_TRACKER_NETWORK_MESSAGE_DISPLAYED = "trackerNetworkMessageDisplayed"
        const val METRIC_PRIVACY_DASH_CLICKED_FROM_ONBOARDING = "privacyDashClickedFromOnboarding"
        const val METRIC_FIRE_BUTTON_PROMPT_DISPLAYED = "fireButtonPromptDisplayed"
        const val METRIC_FIRE_BUTTON_CLICKED_FROM_ONBOARDING = "fireButtonClickedFromOnboarding"
        const val METRIC_FINAL_ONBOARDING_SCREEN_DISPLAYED = "finalOnboardingScreenDisplayed"
        const val METRIC_SECOND_SITE_VISIT = "secondSiteVisit"
        const val METRIC_SECOND_SERP_VISIT = "secondSerpVisit"
    }
}
