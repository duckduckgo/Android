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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
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
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
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
) : OnboardingDesignExperimentManager, MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    private var isExperimentEnabled: Boolean? = null
    private var onboardingDesignExperimentCohort: OnboardingDesignExperimentCohort? = null

    override fun onCreate(owner: LifecycleOwner) {
        coroutineScope.launch {
            setCachedProperties()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        coroutineScope.launch {
            setCachedProperties()
        }
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


    override fun isAnyExperimentEnabled() =
        onboardingDesignExperimentToggles.buckOnboarding().isEnabled() ||
            onboardingDesignExperimentToggles.bbOnboarding().isEnabled()
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

}
