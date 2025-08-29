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

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BB
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BUCK
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.MODIFIED_CONTROL
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor.TABLET
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface OnboardingDesignExperimentManager {
    suspend fun enroll()
    fun isAnyExperimentEnrolledAndEnabled(): Boolean
    fun isModifiedControlEnrolledAndEnabled(): Boolean
    fun isBuckEnrolledAndEnabled(): Boolean
    fun isBbEnrolledAndEnabled(): Boolean
    suspend fun fireIntroScreenDisplayedPixel()
    suspend fun fireComparisonScreenDisplayedPixel()
    suspend fun fireChooseBrowserPixel()
    suspend fun fireSetDefaultRatePixel()
    suspend fun fireSetAddressBarDisplayedPixel()
    suspend fun fireAddressBarSetTopPixel()
    suspend fun fireAddressBarSetBottomPixel()
    suspend fun fireSearchOrNavCustomPixel()
    suspend fun firePrivacyDashClickedFromOnboardingPixel()
    suspend fun fireFireButtonClickedFromOnboardingPixel()
    suspend fun fireInContextDialogShownPixel(cta: Cta?)
    suspend fun fireOptionSelectedPixel(cta: Cta, index: Int)
    suspend fun fireSiteSuggestionOptionSelectedPixel(index: Int)
    suspend fun onWebPageFinishedLoading(url: String?)
    fun getCohort(): String?
    suspend fun waitForPrivacyConfig(): Boolean
    suspend fun isWaitForLocalPrivacyConfigEnabled(): Boolean
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = OnboardingDesignExperimentManager::class,
)
@SingleInstanceIn(AppScope::class)
class RealOnboardingDesignExperimentManager @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    private val onboardingExperimentMetricsPixelPlugin: OnboardingExperimentMetricsPixelPlugin,
    private val onboardingDesignExperimentCountDataStore: OnboardingDesignExperimentCountDataStore,
    private val pixel: Pixel,
    private val appBuildConfig: AppBuildConfig,
    private val deviceInfo: DeviceInfo,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : OnboardingDesignExperimentManager, PrivacyConfigCallbackPlugin {

    private var isExperimentEnabled: Boolean? = null
    private var onboardingDesignExperimentCohort: OnboardingDesignExperimentCohort? = null

    private var privacyPersisted: Boolean = false

    override suspend fun waitForPrivacyConfig(): Boolean {
        while (!privacyPersisted) {
            delay(10)
        }
        return true
    }

    override suspend fun isWaitForLocalPrivacyConfigEnabled(): Boolean {
        return onboardingDesignExperimentToggles.waitForLocalPrivacyConfig().isEnabled()
    }

    override fun onPrivacyConfigPersisted() {
        privacyPersisted = true
        coroutineScope.launch {
            setCachedProperties()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        coroutineScope.launch {
            setCachedProperties()
        }
    }

    override fun getCohort(): String? {
        return onboardingDesignExperimentCohort?.cohortName
    }

    /**
     * Enrolls the user in the onboarding design experiment if they are eligible.
     * Eligibility is determined by the device's Android version, form factor, and whether the user is a returning user.
     */
    override suspend fun enroll() {
        withContext(dispatcherProvider.io()) {
            if (isEligibleForEnrolment()) {
                onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().enroll()
                setCachedProperties()
            }
        }
    }

    override fun isAnyExperimentEnrolledAndEnabled() = isModifiedControlEnrolledAndEnabled() ||
        isBuckEnrolledAndEnabled() ||
        isBbEnrolledAndEnabled()

    override fun isModifiedControlEnrolledAndEnabled(): Boolean = isExperimentEnabled == true &&
        onboardingDesignExperimentCohort == MODIFIED_CONTROL

    override fun isBuckEnrolledAndEnabled(): Boolean = isExperimentEnabled == true &&
        onboardingDesignExperimentCohort == BUCK

    override fun isBbEnrolledAndEnabled(): Boolean = isExperimentEnabled == true &&
        onboardingDesignExperimentCohort == BB

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

    override suspend fun fireSearchOrNavCustomPixel() {
        onboardingExperimentMetricsPixelPlugin.getSearchOrNavCustomMetric()?.fire()
    }

    override suspend fun firePrivacyDashClickedFromOnboardingPixel() {
        onboardingExperimentMetricsPixelPlugin.getPrivacyDashClickedFromOnboardingMetric()?.fire()
    }

    override suspend fun fireFireButtonClickedFromOnboardingPixel() {
        onboardingExperimentMetricsPixelPlugin.getFireButtonClickedFromOnboardingMetric()?.fire()
    }

    private suspend fun fireSecondSiteVisitPixel() {
        onboardingDesignExperimentCountDataStore.getSiteVisitCount().takeIf { it < 2 }?.let {
            if (onboardingDesignExperimentCountDataStore.increaseSiteVisitCount() == 2) {
                onboardingExperimentMetricsPixelPlugin.getSecondSiteVisitMetric()?.fire()
            }
        }
    }

    private suspend fun fireSecondSerpVisitPixel() {
        onboardingDesignExperimentCountDataStore.getSerpVisitCount().takeIf { it < 2 }?.let {
            if (onboardingDesignExperimentCountDataStore.increaseSerpVisitCount() == 2) {
                onboardingExperimentMetricsPixelPlugin.getSecondSerpVisitMetric()?.fire()
            }
        }
    }

    override suspend fun fireInContextDialogShownPixel(cta: Cta?) {
        when (cta) {
            is DaxBubbleCta -> {
                when (cta) {
                    is DaxBubbleCta.DaxIntroSearchOptionsCta -> fireTryASearchDisplayedPixel()
                    is DaxBubbleCta.DaxIntroVisitSiteOptionsCta -> fireVisitSitePromptDisplayedNewTabPixel()
                    is DaxBubbleCta.DaxEndCta -> fireFinalOnboardingScreenDisplayedPixel()
                    is DaxBubbleCta.DaxPrivacyProCta -> Unit // No pixel for this CTA
                }
            }
            is OnboardingDaxDialogCta -> {
                when (cta) {
                    is OnboardingDaxDialogCta.DaxSerpCta -> fireMessageOnSerpDisplayedPixel()
                    is OnboardingDaxDialogCta.DaxSiteSuggestionsCta -> fireVisitSitePromptDisplayedAdjacentPixel()
                    is OnboardingDaxDialogCta.DaxTrackersBlockedCta -> fireTrackersBlockedMessageDisplayedPixel()
                    is OnboardingDaxDialogCta.DaxNoTrackersCta -> fireNoTrackersMessageDisplayedPixel()
                    is OnboardingDaxDialogCta.DaxMainNetworkCta -> fireTrackerNetworkMessageDisplayedPixel()
                    is OnboardingDaxDialogCta.DaxFireButtonCta -> fireFireButtonPromptDisplayedPixel()
                    is OnboardingDaxDialogCta.DaxEndCta -> fireFinalOnboardingScreenDisplayedPixel()
                }
            }
        }
    }

    override suspend fun fireOptionSelectedPixel(
        cta: Cta,
        index: Int,
    ) {
        when (cta) {
            is DaxBubbleCta.DaxIntroSearchOptionsCta -> {
                when (index) {
                    0 -> fireFirstSearchSuggestionPixel()
                    1 -> fireSecondSearchSuggestionPixel()
                    2 -> fireThirdSearchSuggestionPixel()
                    else -> Unit // only 3 options are available
                }
            }
            is DaxBubbleCta.DaxIntroVisitSiteOptionsCta,
            is OnboardingDaxDialogCta.DaxSiteSuggestionsCta,
            -> fireSiteSuggestionOptionSelectedPixel(index)
        }
    }

    override suspend fun fireSiteSuggestionOptionSelectedPixel(index: Int) {
        when (index) {
            0 -> fireFirstSiteSuggestionPixel()
            1 -> fireSecondSiteSuggestionPixel()
            2 -> fireThirdSiteSuggestionPixel()
            else -> Unit // only 3 options are available
        }
    }

    override suspend fun onWebPageFinishedLoading(url: String?) {
        if (url == null) return
        if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
            fireSecondSerpVisitPixel()
        } else {
            fireSecondSiteVisitPixel()
        }
    }

    private suspend fun fireTryASearchDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTryASearchDisplayedMetric()?.fire()
    }

    private suspend fun fireVisitSitePromptDisplayedNewTabPixel() {
        onboardingExperimentMetricsPixelPlugin.getVisitSitePromptDisplayedNewTabMetric()?.fire()
    }

    private suspend fun fireMessageOnSerpDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getMessageOnSerpDisplayedMetric()?.fire()
    }

    private suspend fun fireVisitSitePromptDisplayedAdjacentPixel() {
        onboardingExperimentMetricsPixelPlugin.getVisitSitePromptDisplayedAdjacentMetric()?.fire()
    }

    private suspend fun fireTrackersBlockedMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTrackersBlockedMessageDisplayedMetric()?.fire()
    }

    private suspend fun fireNoTrackersMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getNoTrackersMessageDisplayedMetric()?.fire()
    }

    private suspend fun fireTrackerNetworkMessageDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getTrackerNetworkMessageDisplayedMetric()?.fire()
    }

    private suspend fun fireFireButtonPromptDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getFireButtonPromptDisplayedMetric()?.fire()
    }

    private suspend fun fireFinalOnboardingScreenDisplayedPixel() {
        onboardingExperimentMetricsPixelPlugin.getFinalOnboardingScreenDisplayedMetric()?.fire()
    }

    private suspend fun fireFirstSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getFirstSearchSuggestionMetric()?.fire()
    }

    private suspend fun fireSecondSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getSecondSearchSuggestionMetric()?.fire()
    }

    private suspend fun fireThirdSearchSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getThirdSearchSuggestionMetric()?.fire()
    }

    private suspend fun fireFirstSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getFirstSiteSuggestionMetric()?.fire()
    }

    private suspend fun fireSecondSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getSecondSiteSuggestionMetric()?.fire()
    }

    private suspend fun fireThirdSiteSuggestionPixel() {
        onboardingExperimentMetricsPixelPlugin.getThirdSiteSuggestionMetric()?.fire()
    }

    private suspend fun isEligibleForEnrolment(): Boolean = isAtLeastAndroid11() && !isTablet() && !isReturningUser()

    private fun isTablet(): Boolean =
        deviceInfo.formFactor() == TABLET

    private suspend fun isReturningUser(): Boolean =
        appBuildConfig.isAppReinstall()

    private fun isAtLeastAndroid11(): Boolean =
        appBuildConfig.sdkInt >= 30

    private suspend fun setCachedProperties() {
        withContext(dispatcherProvider.io()) {
            onboardingDesignExperimentCohort = getEnrolledAndEnabledExperimentCohort()
            isExperimentEnabled = onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().isEnabled()
        }
    }

    private suspend fun getEnrolledAndEnabledExperimentCohort(): OnboardingDesignExperimentCohort? {
        val cohort = onboardingDesignExperimentToggles.onboardingDesignExperimentAug25().getCohort()

        return when (cohort?.name) {
            MODIFIED_CONTROL.cohortName -> MODIFIED_CONTROL
            BUCK.cohortName -> BUCK
            BB.cohortName -> BB
            else -> null
        }
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }
}
